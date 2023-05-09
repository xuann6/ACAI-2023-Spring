import onnx
import numpy as np

def single_input_sum_elimination(onnx_model):
    graph = onnx_model.graph
    a=0
    for id in range(len(graph.node)):
        if graph.node[id-a].op_type == 'Sum':
            if len(graph.node[id-a].input) == 1:
                bad_node_in=graph.node[id-a].input[0]
                bad_node_out=graph.node[id-a].output[0]
                print("find bad sum node")
                for id2, node2 in enumerate(graph.node):
                    for i, input_node in enumerate(node2.input):
                        if bad_node_out == input_node:
                            graph.node[id2].input[i] = bad_node_in
                            print("change bad sum node")
                graph.node.remove(graph.node[id-a])
                a+=1
    info_model = onnx.helper.make_model(graph)
    onnx_model = onnx.shape_inference.infer_shapes(info_model)
    return onnx_model

def zero_padding_elimination(onnx_model):
    graph = onnx_model.graph
    a=0
    for id, node in enumerate(graph.node):
        if graph.node[id-a].op_type == 'Pad':
            for elem in graph.node[id-a].attribute:
                if elem.name=="pads" and elem.ints==[0, 0, 0, 0]:
                    bad_node_in=graph.node[id-a].input[0]
                    bad_node_out=graph.node[id-a].output[0]
                    print("find bad padding node")
                    for id2, node2 in enumerate(graph.node):
                        for i, input_node in enumerate(node2.input):
                            if bad_node_out == input_node:
                                graph.node[id2].input[i] = bad_node_in
                                print("change bad padding node")
                    graph.node.remove(graph.node[id-a])
                    a+=1
    info_model = onnx.helper.make_model(graph)
    onnx_model = onnx.shape_inference.infer_shapes(info_model)
    return onnx_model

def zero_dim_input_elimination(onnx_model):
    graph = onnx_model.graph
    a=0
    for id, node in enumerate(graph.node):
        if graph.node[id-a].op_type == 'Reshape':
            for i in range(len(graph.initializer)):
                if graph.initializer[i-a].name == graph.node[id-a].input[0]:
                    if graph.initializer[i-a].dims == [0]:
                        bad_node_out=graph.node[id].output[0]
                        for id2, node2 in enumerate(graph.node):
                            for i, input_node in enumerate(node2.input):
                                if bad_node_out == input_node:
                                    graph.node[id2].input.remove(bad_node_out)
                                    print("change bad Reshape node")
                        print(graph.initializer[i].name)
                        graph.initializer.remove(graph.initializer[i-a])
                        graph.node.remove(graph.node[id-a])
                        a+=1
            
    info_model = onnx.helper.make_model(graph)
    onnx_model = onnx.shape_inference.infer_shapes(info_model)
    return onnx_model



onnx_model = onnx.load("bad_lenet.onnx")

optimized_model = zero_padding_elimination(onnx_model)
optimized_model = zero_dim_input_elimination(optimized_model)
optimized_model = single_input_sum_elimination(optimized_model)
onnx.save(optimized_model, "good_lenet.onnx")
