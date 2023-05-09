import onnx
import numpy as np

# turn all nodes with active function relu into prelu


def prelu(onnx_model):
    graph = onnx_model.graph

    # recursive whole graph
    for id in range(len(graph.node)):
        # find ReLU operator
        if graph.node[id].op_type == 'Relu':
            # make new node (I/O nodes stay the same)
            new_prelu_node = onnx.helper.make_node(
                "PRelu",
                inputs=[graph.node[id].input[0]],
                outputs=[graph.node[id].output[0]],
            )

            # remove the original node and insert new node using Prelu operator
            graph.node.remove(graph.node[id])
            graph.node.insert(id, new_prelu_node)
    return onnx_model


onnx_model = onnx.load("lenet.onnx")
onnx_model = prelu(onnx_model)
print("convert sucess")
onnx.save(onnx_model, "new_lenet.onnx")
