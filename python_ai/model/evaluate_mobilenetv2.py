import os
import numpy as np
import tensorflow as tf

from tensorflow.keras import layers, models
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    accuracy_score,
    roc_auc_score
)

# ============================================================
# TrueProof - MobileNetV2 Test Evaluation
# 0 = EDITED / FAKE
# 1 = ORIGINAL / GENUINE
# Output = P(GENUINE)
# ============================================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

TEST_DIR = os.path.join(BASE_DIR, "..", "dataset", "test")
MODEL_PATH = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2.h5")

IMG_SIZE = 224

CLASS_NAMES = ["fake", "genuine"]
LABEL_MAP = {
    "fake": 0,
    "genuine": 1
}


# ============================================================
# BUILD SAME MODEL ARCHITECTURE
# ============================================================

def build_model():

    inputs = layers.Input(
        shape=(IMG_SIZE, IMG_SIZE, 3),
        name="image"
    )

    # MobileNetV2 expects preprocessing internally
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3),
        include_top=False,
        weights=None
    )

    x = base_model(x, training=False)

    x = layers.GlobalAveragePooling2D()(x)

    x = layers.Dropout(0.30)(x)

    x = layers.Dense(
        128,
        activation="relu"
    )(x)

    x = layers.Dropout(0.30)(x)

    outputs = layers.Dense(
        1,
        activation="sigmoid",
        name="genuine_probability"
    )(x)

    model = models.Model(
        inputs=inputs,
        outputs=outputs
    )

    return model


# ============================================================
# LOAD IMAGE
# ============================================================

def load_image(path):

    image = tf.keras.utils.load_img(
        path,
        target_size=(IMG_SIZE, IMG_SIZE),
        color_mode="rgb"
    )

    image = tf.keras.utils.img_to_array(image)

    # IMPORTANT:
    # Keep RAW 0-255 here.
    # MobileNetV2 preprocessing is inside the model.
    image = image.astype(np.float32)

    return image


# ============================================================
# GET TEST FILES
# ============================================================

def get_test_files():

    files = []
    labels = []

    for class_name in CLASS_NAMES:

        class_dir = os.path.join(
            TEST_DIR,
            class_name
        )

        if not os.path.isdir(class_dir):
            raise FileNotFoundError(
                f"Missing directory: {class_dir}"
            )

        for root, _, filenames in os.walk(class_dir):

            for filename in sorted(filenames):

                if filename.lower().endswith(
                    (".png", ".jpg", ".jpeg", ".bmp")
                ):

                    path = os.path.join(
                        root,
                        filename
                    )

                    files.append(path)
                    labels.append(
                        LABEL_MAP[class_name]
                    )

    return files, np.array(labels, dtype=np.int32)


# ============================================================
# MAIN
# ============================================================

def main():

    print("=" * 60)
    print("TrueProof - MobileNetV2 Test Evaluation")
    print("=" * 60)

    print()
    print("Model:")
    print(MODEL_PATH)

    if not os.path.isfile(MODEL_PATH):
        raise FileNotFoundError(
            f"Model not found: {MODEL_PATH}"
        )

    print()
    print("Loading test files...")

    files, y_true = get_test_files()

    print(f"Total test images : {len(files)}")
    print(
        f"Fake / Edited    : {np.sum(y_true == 0)}"
    )
    print(
        f"Genuine / Original: {np.sum(y_true == 1)}"
    )

    print()
    print("Building MobileNetV2 architecture...")

    model = build_model()

    print("Loading winning weights...")

    # IMPORTANT:
    # Do NOT use load_model().
    # by_name=True    -> match weights to layers by their name,
    #                    not by position in the file. This is what
    #                    fixes the (3,3,3,32) vs (1,1,160,960) error,
    #                    which happens when the H5 file's flattened
    #                    weight order doesn't line up with a model
    #                    that has a nested base_model sub-layer.
    # skip_mismatch=False -> strict load for final evaluation. Every
    #                    named layer must match exactly in shape, or
    #                    this raises instead of silently skipping a
    #                    layer and giving an invalid evaluation.
    model.load_weights(
        MODEL_PATH,
        by_name=True,
        skip_mismatch=False
    )

    print("Weights loaded successfully.")

    print()
    print("Running predictions...")

    x_test = np.stack(
        [load_image(path) for path in files],
        axis=0
    )

    probabilities = model.predict(
        x_test,
        batch_size=32,
        verbose=1
    ).reshape(-1)

    # P(genuine) >= 0.5 => ORIGINAL
    y_pred = (probabilities >= 0.5).astype(np.int32)

    # ========================================================
    # METRICS
    # ========================================================

    accuracy = accuracy_score(
        y_true,
        y_pred
    )

    auc = roc_auc_score(
        y_true,
        probabilities
    )

    cm = confusion_matrix(
        y_true,
        y_pred
    )

    print()
    print("=" * 60)
    print("RESULTS")
    print("=" * 60)

    print()
    print(f"Accuracy : {accuracy * 100:.2f}%")
    print(f"ROC-AUC  : {auc:.4f}")

    print()
    print("Confusion Matrix")
    print("----------------")

    print(cm)

    print()
    print("Classification Report")
    print("---------------------")

    print(
        classification_report(
            y_true,
            y_pred,
            target_names=[
                "EDITED / FAKE",
                "ORIGINAL / GENUINE"
            ],
            digits=4
        )
    )

    # ========================================================
    # CONFIDENCE DISTRIBUTION
    # ========================================================

    genuine_probs = probabilities[y_true == 1]
    fake_probs = probabilities[y_true == 0]

    print()
    print("=" * 60)
    print("CONFIDENCE DISTRIBUTION")
    print("=" * 60)

    print()
    print("Genuine test images:")
    print(f"  Mean P(genuine): {np.mean(genuine_probs):.6f}")
    print(f"  Min            : {np.min(genuine_probs):.6f}")
    print(f"  Max            : {np.max(genuine_probs):.6f}")
    print(f"  Std            : {np.std(genuine_probs):.6f}")

    print()
    print("Fake test images:")
    print(f"  Mean P(genuine): {np.mean(fake_probs):.6f}")
    print(f"  Min            : {np.min(fake_probs):.6f}")
    print(f"  Max            : {np.max(fake_probs):.6f}")
    print(f"  Std            : {np.std(fake_probs):.6f}")

    # ========================================================
    # WRONG PREDICTIONS
    # ========================================================

    wrong = np.where(
        y_true != y_pred
    )[0]

    print()
    print("=" * 60)
    print(f"WRONG PREDICTIONS: {len(wrong)}")
    print("=" * 60)

    for index in wrong[:30]:

        actual = (
            "GENUINE"
            if y_true[index] == 1
            else "FAKE"
        )

        predicted = (
            "GENUINE"
            if y_pred[index] == 1
            else "FAKE"
        )

        print()
        print(f"File      : {files[index]}")
        print(f"Actual    : {actual}")
        print(f"Predicted : {predicted}")
        print(
            f"P(genuine): {probabilities[index]:.6f}"
        )

    print()
    print("=" * 60)
    print("MobileNetV2 test evaluation complete.")
    print("=" * 60)


if __name__ == "__main__":
    main()