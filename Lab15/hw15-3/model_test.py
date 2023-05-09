
import numpy as np
import onnx
import onnxruntime

k = np.ones((1, 1, 28, 28))
k = k.astype(np.float32)
pat1 = "lenet.onnx"
session = onnxruntime.InferenceSession(pat1)
session.get_modelmeta()
first_input_name = session.get_inputs()[0].name
first_output_name = session.get_outputs()[0].name
results1 = session.run([first_output_name], {
    first_input_name: k})


pat = "lenet_CHANNEL.onnx"
session = onnxruntime.InferenceSession(pat)
session.get_modelmeta()
first_input_name = session.get_inputs()[0].name
first_output_name = session.get_outputs()[0].name
results2 = session.run([first_output_name], {
    first_input_name: k})


# SET-UP PRECISION UNIT: %
# absolute(a - b) <= (atol + rtol * absolute(b))
precision = 0.5
print("\n\nTolerance precision: ", precision, " percent.")
print("Result match(T/F): ",
      np.allclose(results1, results2, rtol=precision/100))
