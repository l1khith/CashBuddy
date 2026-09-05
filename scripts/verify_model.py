import os
import sys
import onnx

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

model_path = "androidApp/src/main/assets/models/distilbert-transaction-classifier-quantized.onnx"
print(f"Loading {model_path}...")
model = onnx.load(model_path)

print("\nInputs:")
for inp in model.graph.input:
    shape = [d.dim_value if d.dim_value > 0 else d.dim_param for d in inp.type.tensor_type.shape.dim]
    print(f"  Name: {inp.name}, Shape: {shape}")

print("\nOutputs:")
for out in model.graph.output:
    shape = [d.dim_value if d.dim_value > 0 else d.dim_param for d in out.type.tensor_type.shape.dim]
    print(f"  Name: {out.name}, Shape: {shape}")

print(f"\nOpset: {model.opset_import[0].version}")

has_int8 = any(
    init.data_type in (onnx.TensorProto.INT8, onnx.TensorProto.UINT8)
    for init in model.graph.initializer
)
print(f"Has INT8/UINT8 quantized weights: {has_int8}")
print(f"Model file size: {os.path.getsize(model_path) / 1024 / 1024:.2f} MB")
