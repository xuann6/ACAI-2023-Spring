import numpy as np
import math
import onnx
import numpy as np


def decomposeConvByChannel(onnx_model, limit):

    graph = onnx_model.graph
    id = 0

    # use while in order to control the value of id during the loop
    for id, nd in enumerate(graph.node):
        count = 0
        node = graph.node[id]

        if node.op_type == 'Conv':

            input_shape = [0, 0, 0, 0]

            # Find the weight and bias of convolution node
            for initializer in graph.initializer:
                if initializer.name == node.input[1]:
                    conv_weight = initializer

                try:
                    if initializer.name == node.input[2]:
                        conv_bias = node.input[2]
                except:
                    conv_bias = None

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

            kernel_channel = input_shape[1]
            input_channel = input_shape[1]

            # if limit < kernel_channel:
            #    print("Node id: ", id,
            #           ". Limit range error, value of limitation should be larger than kernel channel.")
            # 　    id += 1
            #   continue

            # sep_num   : recording original input node need to be saperated into how many nodes
            # pad_num   : recording original input node need to be padded (pad in conv + pad used to satisfy I/O size)
            sep_num = 1

            # If CHANNEL is greater than LIMIT, or else the we don't have to separate
            if input_channel > limit:

                # to avoid divided 0 error
                if kernel_channel - 1 == 0:
                    sep_num = 1
                else:
                    sep_num = math.ceil(input_channel / limit)

                print("sep_num: ", sep_num)

                # DONT need to PAD in this dimension

                offset = 0

                try:
                    tensor_input = [conv_weight.name, conv_bias.name]
                except:
                    tensor_input = [conv_weight.name]

                # Recording information for CONCAT node
                conv_list = []
                for i in range(sep_num):

                    slice_info_node = onnx.helper.make_node(
                        'Slice',
                        name=node.input[0]+'_slice_info_'+str(i),
                        inputs=tensor_input,
                        outputs=[node.input[0]+'_slice_info_'+str(i)],

                        # ===============================================   TBD  ================================================
                        # Default axes & steps,
                        # To slice by width, basically we only need to modify fourth dimension.
                        # However, I tried to extract the padding operation from original conv node,
                        # in the third dimension we have  consider the effect of pre-padding
                        starts=[0, offset, 0, 0],
                        ends=[conv_weight.dims[0], offset + limit,
                              conv_weight.dims[2], conv_weight.dims[3]],
                        # ===============================================   TBD  ================================================
                    )

                    graph.node.insert(id + count, slice_info_node)
                    count += 1

                    slice_node = onnx.helper.make_node(
                        'Slice',
                        name=node.input[0]+'_slice_'+str(i),
                        inputs=[node.input[0]],
                        outputs=[node.input[0]+'_slice_'+str(i)],

                        # ===============================================   TBD  ================================================
                        # Default axes & steps,
                        # To slice by width, basically we only need to modify fourth dimension.
                        # However, I tried to extract the padding operation from original conv node,
                        # in the third dimension we have  consider the effect of pre-padding
                        starts=[0, offset, 0, 0],
                        ends=[np.iinfo(np.int32).max, offset + limit,
                              np.iinfo(np.int32).max, np.iinfo(np.int32).max],
                        # ===============================================   TBD  ================================================
                    )
                    graph.node.insert(id + count, slice_node)
                    count += 1
                    offset += limit

                    conv_node = onnx.helper.make_node(
                        'Conv',
                        name=node.input[0]+'new_conv_'+str(i),
                        # ===============================================   WARNING  =========================================
                        # Make sure the inputs including weight & bias
                        # node.input[1] is required (original weight information)
                        inputs=[slice_node.name, slice_info_node.name],
                        # ===============================================   WARNING  =========================================
                        outputs=[node.input[0]+'_conv_'+str(i)],
                    )
                    # Already padding before slice, DONT have to pad again here
                    attrs = node.attribute
                    conv_node.attribute.extend(attrs)

                    graph.node.insert(id + count, conv_node)
                    count += 1

                    conv_list.append(node.input[0]+'_conv_'+str(i))

                sum_node = onnx.helper.make_node(
                    'Sum',
                    name=node.input[0]+'_add',
                    inputs=[inp for inp in conv_list],
                    outputs=[node.output[0]],
                )

                graph.node.insert(id + count, sum_node)
                count += 1

                # To make sure the loop detection will skip the added nodes (avoiding infinite loop)
                # id = id + count
                graph.node.remove(node)
            else:
                id += 1
        else:
            id += 1
    return onnx_model
