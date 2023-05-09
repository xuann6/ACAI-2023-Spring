import numpy as np
import math
import onnx
import numpy as np


def decomposeConvByHeight(onnx_model, limit):

    graph = onnx_model.graph
    id = 0

    # use while in order to control the value of id during the loop
    for id, nd in enumerate(graph.node):

        count = 0
        node = graph.node[id]

        if node.op_type == 'Conv':

            input_shape = [0, 0, 0, 0]
            kernel_shape = node.attribute[2].ints
            kernel_height = kernel_shape[0]

            # NOTICE: input information is under graph proto
            flag = False
            for input in graph.value_info:
                for inp in node.input:
                    if input.name == inp:
                        # ===============================================   WARNING  ============================================
                        # only need to extract the third and fourth dimension of tensor (HEIGHT x WIDTH)
                        input_shape = [input.type.tensor_type.shape.dim[0].dim_value,
                                       input.type.tensor_type.shape.dim[1].dim_value,
                                       input.type.tensor_type.shape.dim[2].dim_value,
                                       input.type.tensor_type.shape.dim[3].dim_value]
                        flag = True
                        # ===============================================   WARNING  ============================================
                if flag:
                    break

            flag = False
            for input in graph.value_info:
                for out in node.output:
                    if input.name == out:
                        # ===============================================   WARNING  ============================================
                        # only need to extract the third and fourth dimension of tensor (HEIGHT x WIDTH)
                        output_shape = [input.type.tensor_type.shape.dim[0].dim_value,
                                        input.type.tensor_type.shape.dim[1].dim_value,
                                        input.type.tensor_type.shape.dim[2].dim_value,
                                        input.type.tensor_type.shape.dim[3].dim_value]

                        flag = True
                    # ===============================================   WARNING  ============================================
                if flag:
                    break

            if input_shape == [0, 0, 0, 0]:
                for input in graph.input:
                    for inp in node.input:
                        if input.name == inp:
                            # ===============================================   WARNING  ============================================
                            # only need to extract the third and fourth dimension of tensor (HEIGHT x WIDTH)
                            input_shape = [input.type.tensor_type.shape.dim[0].dim_value,
                                           input.type.tensor_type.shape.dim[1].dim_value,
                                           input.type.tensor_type.shape.dim[2].dim_value,
                                           input.type.tensor_type.shape.dim[3].dim_value]

                            flag = True
                        # ===============================================   WARNING  ============================================
                    if flag:
                        break

            # TBD after DIMENSION MODE added
            input_height = input_shape[2]
            output_height = output_shape[2]

            if limit < kernel_height:
                print("Node id: ", id,
                      ". Limit range error, value of limitation should be larger than kernel channel.")
                id += 1
                continue

            # sep_num   : recording original input node need to be saperated into how many nodes
            # pad_num   : recording original input node need to be padded (pad in conv + pad used to satisfy I/O size)
            sep_num = 1
            pad_num = 0

            # If WIDTH is greater than LIMIT, or else the we don't have to separate
            if input_height > limit:

                # to avoid divided 0 error
                if kernel_height - 1 == 0:
                    sep_num = 1
                else:
                    sep_num = math.ceil(
                        output_height / (limit - kernel_height + 1))
                    # sep_num = math.ceil(output_width / (kernel_width - 1))

                # conv_node.attribute[0] -> dilation, [1]->strides, [2]->kernel_shape, [3]->pads(optional)
                # Check padding value in original node attributes
                for attr in node.attribute:
                    if attr.name == 'pads':
                        # ===============================================   WARNING  =========================================
                        # The assumption is not stable and could be hazardous
                        #     1. Currently assume the padding parameter inside conv node can solve all sizing problem.
                        #     2. Currently assume "attribute.pads" will always be (index == 3)
                        pad_num += node.attribute[3].ints[0]
                        # ===============================================   WARNING  =========================================

                print("sep_num: ", sep_num)
                print("pad_num: ", pad_num)

                pad_node = onnx.helper.make_node(
                    'Pad',
                    inputs=[node.input[0]],
                    outputs=[node.input[0]+'_pad'],
                    name=node.input[0] + '_pad',
                    mode='constant',
                    # ===============================================   TBD  =================================================
                    # `pads` format should be: [x1_begin, x2_begin, ..., x1_end, x2_end,...]
                    # pads=[0, 0, 0, 0, 0, 0, pad_num, pad_num],
                    pads=[0, 0, pad_num, pad_num, 0, 0, pad_num, pad_num],

                    # <<UPDATES>> : It seems that pad operator in onnx version 13 does not support "axes" in attribute.
                    # All axes are assumed (`[0, 1, ..., input_rank-1]`),
                    # and negative value means counting dimensions from the back.
                    # ===============================================   TBD  =================================================
                )

                graph.node.insert(id + count, pad_node)
                count += 1

                # Recording information for SLICE operator
                # offset    : recording offset for Slice operator
                # useful_num: recording valid data size of each small convolution layer (limit - (kernel_width - 1))
                offset = 0
                useful_num = limit - kernel_height + 1

                # Recording information for CONCAT node
                conv_list = []
                for i in range(sep_num):

                    slice_node = onnx.helper.make_node(
                        'Slice',
                        name=node.input[0]+'_slice_'+str(i),
                        inputs=[node.input[0]+'_pad'],
                        outputs=[node.input[0]+'_slice_'+str(i)],

                        # ===============================================   TBD  ================================================
                        # Default axes & steps,
                        # To slice by width, basically we only need to modify fourth dimension.
                        # However, I tried to extract the padding operation from original conv node,
                        # in the third dimension we have to consider the effect of pre-padding
                        starts=[0, 0, offset, 0],
                        ends=[input_shape[0], input_shape[1],
                              offset + limit, input_shape[3] + 2 * pad_num],
                        # ===============================================   TBD  ================================================
                    )
                    graph.node.insert(id + count, slice_node)
                    count += 1
                    offset += useful_num

                    # To make sure both weight and bias have value
                    try:
                        det_input = [node.input[1], node.input[2]]
                    except:
                        det_input = [node.input[1]]

                    conv_node = onnx.helper.make_node(
                        'Conv',
                        name=node.input[0]+'new_conv_'+str(i),
                        # ===============================================   WARNING  =========================================
                        # Make sure the inputs including weight & bias
                        # node.input[1] is required (original weight information)
                        inputs=[node.input[0]+'_slice_'+str(i)] + det_input,
                        # ===============================================   WARNING  =========================================
                        outputs=[node.input[0]+'_conv_'+str(i)],
                    )
                    # Already padding before slice, DONT have to pad again here
                    attrs = node.attribute
                    for attr in attrs[:]:
                        if attr.name != 'pads':
                            conv_node.attribute.extend([attr])

                    graph.node.insert(id + count, conv_node)
                    count += 1

                    conv_list.append(node.input[0]+'_conv_'+str(i))

                concat_node = onnx.helper.make_node(
                    'Concat',
                    name=node.input[0]+'_concat',
                    inputs=[inp for inp in conv_list],
                    outputs=[node.output[0]],

                    # ===============================================   WARNING  ============================================
                    # Which axis to concat on.
                    # A negative value means counting dimensions from the back.
                    # Accepted range is [-r, r-1] where r = rank(inputs)
                    axis=2,
                    # ===============================================   WARNING  ============================================
                )

                graph.node.insert(id + count, concat_node)
                count += 1

                # To make sure the loop detection will skip the added nodes (avoiding infinite loop)
                # id = id + count
                graph.node.remove(node)
            else:
                id += 1
        else:
            id += 1
    return onnx_model
