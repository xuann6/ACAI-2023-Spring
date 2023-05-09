"""TestBench:

    Output graph:


                Input 
                  |
                  |     1  x 32 x 14 x 14
                  |
                Conv    64 x 32 x 5  x 5 
                  |
                  |
                  |
                Output  Expect: 1 x 64 x 14 x 14

    Assumption : The limitation of input will be 5, 
                 therefore, our convolution node will be separated into 14 smaller ones.

    Purpose    : To make sure our input and output remain the same even the convolutional layer is separated.
"""
import numpy as np
import math
import onnx
import numpy as np
import re

from decompose_conv_by_channel import decomposeConvByChannel
from decompose_conv_by_width import decomposeConvByWidth
from decompose_conv_by_height import decomposeConvByHeight

# Get the inference shape of the model


def infer_shapes(model: onnx.ModelProto) -> onnx.ModelProto:
    try:
        model = onnx.shape_inference.infer_shapes(model)
        print("=== Dumping inference shape SUCCESS! ===")
    except:
        pass
    return model


# Parameter set-up and load model
limit = 64
model_name = "lenet"
mode = 2
mode_select = ['WIDTH', 'HEIGHT', 'CHANNEL']


# Visualize the inference size of original model
model_name = model_name + ".onnx"
onnx_model = onnx.load(model_name)
onnx_model = infer_shapes(onnx_model)
onnx.save(onnx_model, model_name)
print("=== Saving "+model_name+". ===")

# Mode selection and corresponding operation
if mode_select[mode] == 'WIDTH':
    onnx_model = decomposeConvByWidth(onnx_model, limit)
elif mode_select[mode] == 'HEIGHT':
    onnx_model = decomposeConvByHeight(onnx_model, limit)
else:
    onnx_model = decomposeConvByChannel(onnx_model, limit)


onnx_model = infer_shapes(onnx_model)

# Check and save the model
try:
    onnx.checker.check_model(onnx_model)
except onnx.checker.ValidationError as e:
    print('The model is invalid: %s' % e)
else:
    print('=== The model is valid! ===')
onnx.save(onnx_model, re.sub(".onnx", "", model_name) +
          "_"+mode_select[mode]+".onnx")
print("=== Saving "+re.sub(".onnx", "", model_name) +
      "_"+mode_select[mode]+". ===")
