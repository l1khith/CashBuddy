import os
import sys
import shutil
import hashlib
import urllib.request
import numpy as np

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

from transformers import AutoTokenizer
from optimum.onnxruntime import ORTModelForSequenceClassification, ORTQuantizer
from optimum.onnxruntime.configuration import AutoQuantizationConfig
import onnxruntime as ort

MODEL_NAME = "finmigodeveloper/distilbert-transaction-classifier"
TEMP_DIR = "./temp_model"
ASSETS_DIR = "./androidApp/src/main/assets/models"

os.makedirs(TEMP_DIR, exist_ok=True)
os.makedirs(ASSETS_DIR, exist_ok=True)

# 1. Load and save tokenizer
print("1. Downloading and saving tokenizer & vocab...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
tokenizer.save_pretrained(ASSETS_DIR)

vocab_url = "https://huggingface.co/distilbert/distilbert-base-uncased/raw/main/vocab.txt"
vocab_dest = os.path.join(ASSETS_DIR, "vocab.txt")
urllib.request.urlretrieve(vocab_url, vocab_dest)
print(f"   vocab.txt saved to {vocab_dest} ({os.path.getsize(vocab_dest)} bytes)")

# 2. Export to ONNX FP32
fp32_dir = os.path.join(TEMP_DIR, "fp32")
print("2. Exporting model to ONNX FP32 with optimum...")
model_fp32 = ORTModelForSequenceClassification.from_pretrained(
    MODEL_NAME,
    export=True
)
model_fp32.save_pretrained(fp32_dir)
fp32_file = os.path.join(fp32_dir, "model.onnx")
print(f"   FP32 ONNX model size: {os.path.getsize(fp32_file) / 1024 / 1024:.2f} MB")

# 3. Dynamic INT8 Quantization
print("3. Applying dynamic INT8 quantization...")
quantizer = ORTQuantizer.from_pretrained(fp32_dir, file_name="model.onnx")
qconfig = AutoQuantizationConfig.avx2(is_static=False, per_channel=False)
int8_dir = os.path.join(TEMP_DIR, "int8")
quantizer.quantize(save_dir=int8_dir, quantization_config=qconfig)

# Copy quantized model to assets
int8_dest = os.path.join(ASSETS_DIR, "distilbert-transaction-classifier-quantized.onnx")
shutil.copyfile(os.path.join(int8_dir, "model_quantized.onnx"), int8_dest)
int8_size_mb = os.path.getsize(int8_dest) / 1024 / 1024
print(f"   Quantized INT8 model size: {int8_size_mb:.2f} MB")

# 4. Compute SHA256
sha256 = hashlib.sha256()
with open(int8_dest, "rb") as f:
    while chunk := f.read(8192):
        sha256.update(chunk)
model_hash = sha256.hexdigest()
print(f"4. SHA256 Checksum: {model_hash}")
with open(os.path.join(ASSETS_DIR, "model_hash.txt"), "w") as f:
    f.write(model_hash)

# 5. Verify model inference
print("5. Verifying inference with sample transaction strings...")
session = ort.InferenceSession(int8_dest)
id2label = model_fp32.config.id2label
print(f"   Labels ({len(id2label)}): {id2label}")

test_sentences = [
    "Paid INR 450 to Swiggy Bangalore",
    "Uber ride from Indiranagar to Airport",
    "Amazon India order electronics payment",
    "Electricity bill payment for BESCOM",
    "Apollo Pharmacy medicine purchase"
]

for text in test_sentences:
    inputs = tokenizer(text, padding="max_length", max_length=128, truncation=True, return_tensors="np")
    ort_inputs = {
        "input_ids": inputs["input_ids"].astype(np.int64),
        "attention_mask": inputs["attention_mask"].astype(np.int64)
    }
    ort_outputs = session.run(None, ort_inputs)
    logits = ort_outputs[0][0]
    exp_logits = np.exp(logits - np.max(logits))
    probs = exp_logits / np.sum(exp_logits)
    predicted_id = int(np.argmax(probs))
    label_name = id2label[predicted_id] if predicted_id in id2label else id2label.get(str(predicted_id), str(predicted_id))
    print(f"   '{text}' -> {label_name} (Confidence: {probs[predicted_id]:.3f})")

print("\nModel preparation successfully completed!")
