"""
predict.py
----------
Loads the trained CNN (model/trueproof_model.h5) and predicts GENUINE or
FAKE for a single screenshot. This matches exactly what the model was
trained to do — no OCR, no ELA fusion added on top, because the model
itself was trained on structural genuine-vs-edited pairs and should be
judged on that alone for now. (You can always add OCR/ELA as extra
signals later once this baseline is solid.)

USAGE (from python_ai/ folder):
    python model/predict.py path/to/screenshot.jpg

IMPORTANT — resize interpolation must match training exactly:
    train.py's ImageDataGenerator.flow_from_directory(target_size=(224,224))
    resizes every training image using PIL's "nearest" interpolation
    internally. This file deliberately uses the SAME interpolation
    (Image.Resampling.NEAREST) below — using a different one (e.g. PIL's
    default BICUBIC) can flip the prediction completely on the exact same
    image. This bit us once before; don't change this line.
"""

import os
import sys
import numpy as np
import tensorflow as tf
from PIL import Image

IMG_SIZE = (224, 224)
MODEL_PATH = os.path.join(os.path.dirname(__file__), "trueproof_model.h5")

try:
    RESAMPLE_NEAREST = Image.Resampling.NEAREST
except AttributeError:
    RESAMPLE_NEAREST = Image.NEAREST

_model = None


def get_model():
    global _model
    if _model is None:
        if not os.path.exists(MODEL_PATH):
            raise FileNotFoundError(
                f"No trained model found at {MODEL_PATH}. Run 'python model/train.py' first."
            )
        _model = tf.keras.models.load_model(MODEL_PATH)
    return _model


def predict_image(image_path: str) -> dict:
    """Returns {"verdict": "GENUINE"|"FAKE", "confidence_percent": float,
    "genuine_probability": float}"""
    model = get_model()

    img = Image.open(image_path).convert("RGB")
    img = img.resize(IMG_SIZE, resample=RESAMPLE_NEAREST)

    # Plain 0-255 float array — do NOT divide by 255 here, the model's own
    # layers.Rescaling(1./255) layer does that internally.
    arr = np.expand_dims(np.array(img, dtype=np.float32), axis=0)

    genuine_prob = float(model.predict(arr, verbose=0)[0][0])  # 0=fake, 1=genuine
    verdict = "GENUINE" if genuine_prob >= 0.5 else "FAKE"
    confidence = genuine_prob if verdict == "GENUINE" else (1 - genuine_prob)

    return {
        "verdict": verdict,
        "confidence_percent": round(confidence * 100, 2),
        "genuine_probability": round(genuine_prob, 4),
    }


if __name__ == "__main__":
    import json
    if len(sys.argv) < 2:
        print("Usage: python predict.py <image_path>")
        sys.exit(1)
    result = predict_image(sys.argv[1])
    print(json.dumps(result, indent=2))
