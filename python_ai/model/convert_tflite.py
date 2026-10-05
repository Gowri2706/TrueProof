"""
convert_tflite_savedmodel.py
----------------------------

TrueProof:
Keras H5 -> FLOAT32 TFLite

Purpose:
    Convert:
        model/trueproof_model.h5
    into:
        model/trueproof_model.tflite

Important:
    - NO retraining
    - NO quantization
    - FLOAT32 only
    - Keeps the model's internal Rescaling(1./255)
    - Input remains RAW 0-255 float32
    - Output remains P(genuine)
        0.0 = fake/edited
        1.0 = genuine/original
"""

import os
import tensorflow as tf

from tensorflow.python.framework.convert_to_constants import (
    convert_variables_to_constants_v2
)


# =========================================================
# PATHS
# =========================================================

BASE_DIR = os.path.dirname(
    os.path.dirname(
        os.path.abspath(__file__)
    )
)

MODEL_PATH = os.path.join(
    BASE_DIR,
    "model",
    "trueproof_model.h5"
)

TFLITE_PATH = os.path.join(
    BASE_DIR,
    "model",
    "trueproof_model.tflite"
)


# =========================================================
# IMAGE SIZE
# =========================================================

IMG_HEIGHT = 224
IMG_WIDTH = 224
CHANNELS = 3


# =========================================================
# CONVERT
# =========================================================

def convert_model():

    print()
    print("================================================")
    print(" TrueProof H5 -> FLOAT32 TFLite")
    print("================================================")
    print()

    print("TensorFlow:", tf.__version__)
    print()

    # -----------------------------------------------------
    # Check model
    # -----------------------------------------------------

    if not os.path.exists(MODEL_PATH):
        raise FileNotFoundError(
            "H5 model not found:\n"
            + MODEL_PATH
            + "\n\n"
            "Run train.py first."
        )

    print("H5 model:")
    print(MODEL_PATH)
    print()

    # -----------------------------------------------------
    # Load trained model
    # -----------------------------------------------------

    print("Loading trained Keras model...")

    model = tf.keras.models.load_model(
        MODEL_PATH,
        compile=False
    )

    print("Model loaded successfully.")
    print()

    print("Input shape :", model.input_shape)
    print("Output shape:", model.output_shape)
    print()

    # -----------------------------------------------------
    # Print layers
    # -----------------------------------------------------

    print("Model layers:")

    for i, layer in enumerate(model.layers):
        print(
            f"{i:02d}  "
            f"{layer.name:30s} "
            f"{layer.__class__.__name__}"
        )

    print()

    # -----------------------------------------------------
    # IMPORTANT:
    # Model contains Rescaling(1./255)
    #
    # Therefore Android/Python should provide:
    #     RAW 0-255 float32
    #
    # NOT:
    #     0-1 normalized values
    # -----------------------------------------------------

    print("Creating inference function...")

    @tf.function(
        input_signature=[
            tf.TensorSpec(
                shape=[
                    1,
                    IMG_HEIGHT,
                    IMG_WIDTH,
                    CHANNELS
                ],
                dtype=tf.float32,
                name="input"
            )
        ]
    )
    def inference(x):

        return model(
            x,
            training=False
        )

    concrete_func = inference.get_concrete_function()

    print("Inference function created.")
    print()

    # -----------------------------------------------------
    # Freeze variables
    # -----------------------------------------------------

    print("Freezing model variables...")

    frozen_func = convert_variables_to_constants_v2(
        concrete_func
    )

    frozen_graph = frozen_func.graph

    print(
        "Frozen graph nodes:",
        len(
            frozen_graph.as_graph_def().node
        )
    )

    print("Variables frozen successfully.")
    print()

    # -----------------------------------------------------
    # Convert frozen graph to TFLite
    # -----------------------------------------------------

    print("Starting TFLite conversion...")
    print("Quantization: DISABLED")
    print("Datatype: FLOAT32")
    print()

    converter = tf.lite.TFLiteConverter.from_concrete_functions(
        [frozen_func],
        frozen_func.inputs
    )

    # -----------------------------------------------------
    # NO optimization / NO quantization
    # -----------------------------------------------------

    converter.optimizations = []

    # -----------------------------------------------------
    # Only standard TFLite builtin operations
    # -----------------------------------------------------

    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS
    ]

    # -----------------------------------------------------
    # Convert
    # -----------------------------------------------------

    tflite_model = converter.convert()

    print()
    print("TFLite conversion successful.")
    print()

    # -----------------------------------------------------
    # Save
    # -----------------------------------------------------

    with open(
        TFLITE_PATH,
        "wb"
    ) as f:

        f.write(tflite_model)

    # -----------------------------------------------------
    # File information
    # -----------------------------------------------------

    file_size_bytes = os.path.getsize(
        TFLITE_PATH
    )

    file_size_mb = (
        file_size_bytes /
        (1024 * 1024)
    )

    print("================================================")
    print(" SUCCESS")
    print("================================================")
    print()

    print("TFLite file:")
    print(TFLITE_PATH)
    print()

    print(
        "File size:",
        f"{file_size_mb:.2f} MB"
    )

    print()

    print("Input:")
    print("  Shape : [1, 224, 224, 3]")
    print("  Type  : FLOAT32")
    print("  Range : RAW 0-255")
    print()

    print("Output:")
    print("  Shape : [1, 1]")
    print("  Type  : FLOAT32")
    print("  Value : P(genuine)")
    print()

    print("Label mapping:")
    print("  output >= 0.5 -> ORIGINAL")
    print("  output <  0.5 -> EDITED")
    print()

    print("================================================")
    print()


# =========================================================
# RUN
# =========================================================

if __name__ == "__main__":
    convert_model()