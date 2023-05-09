import onnx
import numpy as np
import onnxoptimizer
from ctypes import sizeof


def fuse_bn_into_conv(model):
    for node in model.graph.node:
        if node.op_type == 'Conv':

            # Get convolution node which we're trying to fuse
            conv_node = node
            bn_node = None

            # Find the BatchNormalization node right after Convolution node
            for output in conv_node.output:
                for node2 in model.graph.node:
                    if node2.op_type == 'BatchNormalization' and output == node2.input[0]:
                        bn_node = node2
                        print("found bn node")
                        break
                # Did not found bn_node, break.
                if bn_node is not None:
                    break
            # bn_node found, literally will onyl find a node right behind node
            if bn_node is not None:
                # initialize for fusing
                conv_weight = None
                conv_bias = None
                bna_scale = None
                bn_bias = None
                bn_mean = None
                bn_var = None
                print(1)
                for initializer in model.graph.initializer:
                    if initializer.name == conv_node.input[1]:
                        conv_weight = initializer
                        print(2)
                    if len(conv_node.input) > 2:
                        if initializer.name == conv_node.input[2]:
                            conv_bias = initializer
                    if initializer.name == bn_node.input[1]:
                        bn_scale = initializer
                    if initializer.name == bn_node.input[2]:
                        bn_bias = initializer
                    if initializer.name == bn_node.input[3]:
                        bn_mean = initializer
                    if initializer.name == bn_node.input[4]:
                        bn_var = initializer
                # Is this condition possible?
                if conv_weight == None:
                    print('break')
                    break
                # check=0
                if len(conv_node.input) == 2:
                    bias = np.zeros(conv_weight.dims[0])
                    # conv_bias = onnx.helper.make_tensor(name=conv_node.input[1]+'_bias',
                    #     data_type=onnx.TensorProto.FLOAT,
                    #     dims=[conv_weight.dims[0]],
                    #     vals=np.zeros(conv_weight.dims[0]).flatten().tolist())
                    # model.graph.initializer.append(conv_bias)
                    # conv_node.input.append(bn_bias.name)
                    # check=1
                else:
                    bias = np.frombuffer(conv_bias.raw_data, dtype=np.float32)
                    bias = bias.copy()
                # print(conv_weight.dims)
                # print('type',onnx.TensorProto.FLOAT)
                weight = np.frombuffer(conv_weight.raw_data, dtype=np.float32)
                weight = weight.reshape(
                    conv_weight.dims[0], conv_weight.dims[1], conv_weight.dims[2], conv_weight.dims[3])
                scale = np.frombuffer(bn_scale.raw_data, dtype=np.float32)
                bias2 = np.frombuffer(bn_bias.raw_data, dtype=np.float32)
                mean = np.frombuffer(bn_mean.raw_data, dtype=np.float32)
                var = np.frombuffer(bn_var.raw_data, dtype=np.float32)
                weight = weight.copy()
                scale = scale.copy()
                bias2 = bias2.copy()
                mean = mean.copy()
                var = var.copy()
                # if check==1:
                #     bias=np.zeros(conv_weight.dims[0])
                dim1 = conv_weight.dims[0]
                dim2 = conv_weight.dims[1]
                dim3 = conv_weight.dims[2]
                dim4 = conv_weight.dims[3]
                for i in range(conv_weight.dims[0]):
                    weight[i] = weight[i]*scale[i]/np.sqrt(var[i])
                    bias[i] = bias[i]*scale[i] / \
                        np.sqrt(var[i])+bias2[i]-mean[i] * \
                        scale[i]/np.sqrt(var[i])
                # in order to meet the original shape of initializer?
                weight = weight.reshape(dim1*dim2*dim3*dim4)
                bias = bias.reshape(dim1)
                conv_weight.raw_data = weight.tobytes()
                if len(conv_node.input) == 2:
                    conv_bias = onnx.helper.make_tensor(name=conv_node.input[1]+'_bias',
                                                        data_type=onnx.TensorProto.FLOAT,
                                                        dims=[
                                                            conv_weight.dims[0]],
                                                        vals=bias)
                    model.graph.initializer.append(conv_bias)
                    conv_node.input.append(conv_bias.name)
                else:
                    conv_bias.raw_data = bias.tobytes()
                conv_node.output[0] = bn_node.output[0]
                model.graph.node.remove(bn_node)
    return model


model = onnx.load('vgg13_bn.onnx')

# new_model = onnxoptimizer.optimize(model, ['fuse_bn_into_conv'])
new_model2 = fuse_bn_into_conv(model)


onnx.save(new_model2, "new_vgg13_bn.onnx")
