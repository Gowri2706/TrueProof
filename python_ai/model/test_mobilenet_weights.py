import os
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.applications.mobilenet_v2 import preprocess_input


IMG_SIZE = (224, 224)
MODEL_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "trueproof_model_mobilenetv2.h5"
)


def build_model():
    inputs = layers.Input(
        shape=(224, 224, 3),
        name="input_image"
    )

    x = preprocess_input(inputs)

    base = MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights=None,
        name="mobilenetv2_1.00_224"
    )

    base.trainable = True

    x = base(x)
    x = layers.GlobalAveragePooling2D(name="global_average_pooling2d")(x)
    x = layers.Dropout(0.30, name="dropout")(x)
    x = layers.Dense(128, activation="relu", name="dense")(x)
    x = layers.Dropout(0.30, name="dropout_1")(x)
    outputs = layers.Dense(
        1,
        activation="sigmoid",
        name="genuine_probability"
    )(x)

    return models.Model(
        inputs,
        outputs,
        name="trueproof_mobilenetv2"
    )


print("TensorFlow:", tf.__version__)
print("Keras:", tf.keras.__version__)
print("H5:", MODEL_PATH)
print()

print("Building exact architecture...")
model = build_model()

print("Input :", model.input_shape)
print("Output:", model.output_shape)
print("Layers:", len(model.layers))
print()

print("Loading existing H5 weights...")

try:
    model.load_weights(MODEL_PATH)
    print()
    print("WEIGHTS_LOAD_SUCCESS")
except Exception as e:
    print()
    print("WEIGHTS_LOAD_FAILED")
    print("Error:", e)
    raise SystemExit(1)