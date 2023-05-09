
import numpy as np
import onnx

import onnxruntime
k=np.ones((1,3,224,224))
k=k.astype(np.float32)
pat1="vgg13_bn.onnx"
session = onnxruntime.InferenceSession(pat1)
session.get_modelmeta()
first_input_name = session.get_inputs()[0].name
first_output_name = session.get_outputs()[0].name
results1 = session.run([first_output_name], {
                      first_input_name: k})


pat="new_vgg13_bn.onnx"
session = onnxruntime.InferenceSession(pat)
session.get_modelmeta()
first_input_name = session.get_inputs()[0].name
first_output_name = session.get_outputs()[0].name
results2 = session.run([first_output_name], {
                      first_input_name: k})
print(pat1,":")
print(results1)
print(pat,":")
print(results2)