import onnx
import numpy as np
import onnx

onnx_model = onnx.load("lenet.onnx")


def conv2DFilter3x3Transform(model):
    # search whole graph
    for i, node in enumerate(model.graph.node):
        # find convolution (our target)
        if node.op_type == 'Conv':
            conv_weight = None
            conv_node = node
            for initializer in model.graph.initializer:
                # find the weight of out convolution node
                if initializer.name == conv_node.input[1]:
                    conv_weight = initializer

            # 找出5x5的卷積層，如果超過怎麼辦，很不general
            if conv_weight is not None and conv_weight.dims[2] == 5:
                # 先切成兩塊長方形
                split_node = onnx.helper.make_node(  # 將5x5的卷積層分成3x3的卷積層，由於在兩個維度做切割，所以要做兩次split
                    'Split',
                    inputs=[conv_node.input[1]],
                    outputs=[conv_node.input[1]+"output1",
                             conv_node.input[1]+"output2"],
                    name=conv_node.input[1]+'split1',
                    axis=2,
                    split=[3, 2],
                )

                # 再各自把長方形切成我們要的正方形
                model.graph.node.insert(i+1, split_node)
                split_node1 = onnx.helper.make_node(
                    'Split',
                    inputs=[conv_node.input[1]+"output1"],
                    outputs=[conv_node.input[1]+'0_bias',
                             conv_node.input[1]+'1_bias'],
                    name=conv_node.input[1]+'split2',
                    axis=3,
                    split=[3, 2],
                )
                model.graph.node.insert(i+1, split_node1)
                split_node2 = onnx.helper.make_node(
                    'Split',
                    inputs=[conv_node.input[1]+"output2"],
                    outputs=[conv_node.input[1]+'2_bias',
                             conv_node.input[1]+'3_bias'],
                    name=conv_node.input[1]+'split3',
                    axis=3,
                    split=[3, 2],
                )
                model.graph.node.insert(i+1, split_node2)
                for i2 in range(4):  # 創造4個3x3的卷積層，由於5只能拆成3+2，所以要做pad
                    pad_node1 = onnx.helper.make_node(
                        'Pad',
                        inputs=[conv_node.input[1]+str(i2)+'_bias'],
                        outputs=[conv_node.input[1]+str(i2)+'pad'],
                        name=conv_node.input[1]+str(i2)+'pad1',
                        pads=[0, 0, int(i2 > 1), i2 % 2, 0, 0, 0, 0],
                        value=0.0,
                    )
                    model.graph.node.insert(i+1, pad_node1)
                    convnode1 = onnx.helper.make_node(
                        'Conv',
                        inputs=[conv_node.input[0],
                                conv_node.input[1]+str(i2)+'pad'],
                        outputs=[conv_node.input[1]+str(i2)+'_conv'],
                        kernel_shape=[3, 3],
                        strides=[1, 1],
                        pads=[2*(int(i2 < 2)), 2*((i2+1) % 2),
                              2*(int(i2 > 1)), 2*(i2 % 2)],
                        name=conv_node.input[1]+str(i2)+'_conv',
                        dilations=[1, 1],
                    )
                    model.graph.node.insert(i+1, convnode1)

                sum_node = onnx.helper.make_node(  # 將4個3x3的卷積層相加
                    'Sum',
                    inputs=[conv_node.input[1]+'0_conv', conv_node.input[1]+'1_conv',
                            conv_node.input[1]+'2_conv', conv_node.input[1]+'3_conv'],
                    outputs=[conv_node.output[0]],
                    name=conv_node.input[1]+'sum'
                )
                # separate into 4 convolution nodes and 1 sum node
                # Q: parameter of insertion
                model.graph.node.insert(i+5, sum_node)
                model.graph.node.remove(conv_node)  # remove original conv_node
    return model


for id, node in enumerate(onnx_model.graph.node):
    print("", id, ": ", node.name)
onnx_model = conv2DFilter3x3Transform(onnx_model)
for id, node in enumerate(onnx_model.graph.node):
    print("new_", id, ": ", node.name)
print("success")
onnx.save(onnx_model, "new_lenet.onnx")
