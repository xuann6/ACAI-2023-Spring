
import numpy as np
import onnx

import onnxruntime
k=np.ones((1,1,28,28))
k=k.astype(np.float32)
pat1="lenet.onnx"
session = onnxruntime.InferenceSession(pat1)
session.get_modelmeta()
first_input_name = session.get_inputs()[0].name
first_output_name = session.get_outputs()[0].name
results1 = session.run([first_output_name], {
                      first_input_name: k})

pat="new_lenet.onnx"
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