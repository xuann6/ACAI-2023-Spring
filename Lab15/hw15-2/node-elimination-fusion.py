import onnx
import numpy as np
import onnxoptimizer
from onnx import helper, numpy_helper

"""
Graph Optimizations 1 - Dead-end Elimination
Graph Optimizations 2 - Consecutive Squeeze Fusion

"""


def dead_end_elimination(onnx_model):

    graph = onnx_model.graph
    dead_end_node_list = []

    # SUPPOSE THE ID OF NODES WILL FOLLOW THE ORDER OF APPEARENCE.
    # so the backward method can work
    for id, node in reversed(list(enumerate(graph.node))):
        flag = False

        for output in node.output:

            # Try to find all the possible I/O possibility,
            # if the node is not a dead-end node,
            # set the flag as TRUE
            for id2, node2 in enumerate(graph.node):
                for input in node2.input:
                    if output == input:
                        # print("Find an possible output")
                        flag = True
                        break

        # Get the information of dead-end node,
        # make sure the last node is not added into the list.
        if not flag and id != len(graph.node) - 1:
            print("\nEliminate the dead-end node: ")
            print(graph.node[id].name)
            graph.node.remove(graph.node[id])

    return onnx_model


def consecutive_squeeze_fusion(onnx_model):
    graph = onnx_model.graph

    for id, node in enumerate(graph.node):
        # print(id, graph.node[id].name)
        if graph.node[id].op_type == 'Squeeze':

            # Get squeeze node
            squeeze_node = graph.node[id]

            second_squeeze_node = None

            # Get second squeeze node
            for output in squeeze_node.output:
                for id2, second_node in enumerate(onnx_model.graph.node):
                    if second_node.op_type == 'Squeeze' and output == second_node.input[0]:
                        second_squeeze_node = second_node
                        break
                if second_squeeze_node is not None:
                    break

            # Fuse consecutive squeeze
            if second_squeeze_node is not None:

                # Get axes in both squeezing node
                axes1 = list(squeeze_node.attribute[0].ints)
                axes2 = list(second_squeeze_node.attribute[0].ints)

                # Algorithm of concatenating 2 levels of squeezing
                final_axes = []
                count = 0
                for axe2 in axes2:
                    for axe1 in axes1:
                        if axe1 <= axe2:
                            count += 1

                    final_axes.append(axe2 + count)
                    count = 0
                final_axes = final_axes + axes1
                final_axes.sort()
                print("merge axes into new one: ", final_axes)

                # add the axes(attribute) back to original node
                new_node = onnx.helper.make_node(
                    "Squeeze",
                    name="concatSqueeze",
                    inputs=[graph.node[id].input[0]],
                    outputs=[graph.node[id2].output[0]],
                    axes=final_axes,
                )
                graph.node.remove(graph.node[id2])
                graph.node.remove(graph.node[id])

                graph.node.insert(id, new_node)

    return onnx_model

# customize node adding function for unit testing


def node_adding(axes1, axes2):
    onnx_model = onnx.load("lenet.onnx")

    # decided to add 2~3 SqueezeNode between import/conv3/Conv2D and import/conv3/BiasAdd
    # in order to check my function

    graph = onnx_model.graph
    for id, node in enumerate(graph.node):
        if node.name == "import/conv3/Conv2D":

            # Initialize my nodes
            squeeze_node = onnx.helper.make_node(
                'Squeeze',
                name='squeeze1',
                axes=axes1,
                inputs=['import/conv3/Conv2D:0'],
                outputs=['squeeze1'],
            )
            squeeze_node2 = onnx.helper.make_node(
                'Squeeze',
                name='squeeze2',
                axes=axes2,
                inputs=['squeeze1'],
                outputs=['squeeze2'],
            )

            print("add suqeeze node with axes1: ",
                  squeeze_node.attribute[0].ints)
            graph.node.insert(id+1, squeeze_node)
            print("add suqeeze node with axes2: ",
                  squeeze_node2.attribute[0].ints)
            graph.node.insert(id+2, squeeze_node2)

    onnx.save(onnx_model, "new_lenet.onnx")
    return onnx_model


axes1 = [4]
axes2 = [1, 2, 4]

# Add two Squeeze Node into the graph
onnx_model = node_adding(axes1, axes2)

# Merge consecutive squeeze
onnx_model = consecutive_squeeze_fusion(onnx_model)
onnx.save(onnx_model, "merge_lenet.onnx")

# Eliminate the dead-end nodes
onnx_model = dead_end_elimination(onnx_model)
onnx.save(onnx_model, "after_remove_deadend.onnx")
