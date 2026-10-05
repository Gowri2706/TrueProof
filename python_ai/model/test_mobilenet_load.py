"""
test_mobilenet_load.py
-------------------------
ONE-OFF TEST: tries to load trueproof_model_mobilenetv2.h5 with
tf.keras.models.load_model(), supplying a custom_objects entry for
"TrueDivide" -- the op-layer that Keras 3.12.4 cannot automatically
deserialize from this H5 file (created by an internal TF op layer
from mobilenet_v2.preprocess_input's division step).

This does NOT retrain anything and does NOT modify the H5 file.
It only tests whether providing this compatibility shim lets the
full model (architecture + weights) load in one shot.

USAGE (from python_ai/ folder):
    python model/test_mobilenet_load.py
"""

import os
import tensorflow as tf


class TrueDivide(tf.keras.layers.Layer):
    """Compatibility shim for the TF op-layer that
    mobilenet_v2.preprocess_input's division created when the model
    was originally saved. Just performs elementwise division, same as
    the original op did."""

    def call(self, inputs):
        x, y = inputs
        return tf.math.divide(x, y)

    @classmethod
    def from_config(cls, config):
        return cls()


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2.h5")


def main():
    print("TensorFlow:", tf.__version__)
    print("Keras:", tf.keras.__version__)
    print()
    print("Model path:", MODEL_PATH)

    if not os.path.isfile(MODEL_PATH):
        raise FileNotFoundError(f"Model not found: {MODEL_PATH}")

    print("Loading H5...")

    try:
        model = tf.keras.models.load_model(
            MODEL_PATH,
            compile=False,
            custom_objects={"TrueDivide": TrueDivide}
        )
    except Exception as e:
        print()
        print("MODEL_LOAD_FAILED")
        print(f"Error: {e}")
        raise SystemExit(1)

    print()
    print("MODEL_LOAD_SUCCESS")
    print("Input :", model.input_shape)
    print("Output:", model.output_shape)
    print()
    print("Layer count:", len(model.layers))
    print("First 5 layers:", [l.name for l in model.layers[:5]])
    print("Last 5 layers :", [l.name for l in model.layers[-5:]])


if __name__ == "__main__":
    main()