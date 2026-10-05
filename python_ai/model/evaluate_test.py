"""
evaluate_test.py
------------------
PURPOSE: rule out (or confirm) that your "independent test = 50%" result is
a PREPROCESSING MISMATCH bug — the same class of bug we already found once
in predict.py (PIL default resize vs Keras' NEAREST resize flipping the
sigmoid output almost completely: 0.0001 vs 0.9963 on the SAME image).

WHY THIS SCRIPT AND NOT A HAND-WRITTEN ONE:
    Any hand-written test loop (cv2.imread + manual resize, or
    Image.open().resize() with default interpolation, or manually dividing
    by 255) is exactly how that bug happened before. This script instead
    uses Keras' flow_from_directory() — THE SAME FUNCTION train.py uses —
    so the resize algorithm, pixel dtype, and rescale behaviour are
    IDENTICAL to what the model was trained and validated on. There is no
    room for a silent mismatch.

REQUIRED FOLDER LAYOUT (matches your Phase 1 plan — untouched test set):
    dataset/test/fake/       <- your 100 untouched fake test images
    dataset/test/genuine/    <- your 100 untouched genuine test images

USAGE (from python_ai/ folder):
    python model/evaluate_test.py

HOW TO READ THE RESULT:
    Case A — accuracy jumps back up close to your validation number (~95%+):
        CONFIRMED: it was a preprocessing mismatch in whatever script you
        used before for "independent test". The model is fine. Do NOT
        rebuild with transfer learning — that would fix a problem that
        doesn't exist and waste your remaining time. Wire THIS script's
        preprocessing pattern (flow_from_directory) into predict.py/api.py
        instead, and move on to Android integration.

    Case B — accuracy is still around 50% and still biased toward one class:
        Then it genuinely is a generalization problem (the model overfit to
        something specific about your train/val screenshots — e.g. all from
        the same few phones/apps — that isn't present in the test set).
        THEN transfer learning (MobileNetV2) is the right next move, not
        before.

Either way, this one run tells you which of the two problems you actually
have, instead of guessing.
"""

import os
import numpy as np
import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from sklearn.metrics import classification_report, confusion_matrix

IMG_SIZE = (224, 224)
BATCH_SIZE = 16

DATASET_DIR = os.path.join(os.path.dirname(__file__), "..", "dataset")
TEST_DIR = os.path.join(DATASET_DIR, "test")
MODEL_PATH = os.path.join(os.path.dirname(__file__), "trueproof_model.h5")


def count_images(folder):
    total = 0
    for root, dirs, files in os.walk(folder):
        total += len([f for f in files if f.lower().endswith((".png", ".jpg", ".jpeg"))])
    return total


def main():
    fake_dir = os.path.join(TEST_DIR, "fake")
    genuine_dir = os.path.join(TEST_DIR, "genuine")

    if not os.path.isdir(fake_dir) or not os.path.isdir(genuine_dir):
        print(f"ERROR: expected dataset/test/fake/ and dataset/test/genuine/ folders.")
        print(f"Looked in: {TEST_DIR}")
        raise SystemExit(1)

    n_fake = count_images(fake_dir)
    n_genuine = count_images(genuine_dir)
    print(f"Test images found: fake={n_fake}, genuine={n_genuine}")

    if not os.path.exists(MODEL_PATH):
        print(f"ERROR: model not found at {MODEL_PATH}. Run model/train.py first.")
        raise SystemExit(1)

    model = tf.keras.models.load_model(MODEL_PATH)

    # IMPORTANT: no manual rescale, no manual resize interpolation choice here —
    # this MUST be the exact same call shape as train.py's flow_from_directory,
    # so nothing about preprocessing can silently differ.
    test_datagen = ImageDataGenerator()
    test_gen = test_datagen.flow_from_directory(
        TEST_DIR,
        classes=["fake", "genuine"],   # index 0=fake, 1=genuine — matches train.py
        target_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        class_mode='binary',
        shuffle=False,
    )

    y_true = test_gen.classes
    y_pred_prob = model.predict(test_gen, verbose=1)
    y_pred = (y_pred_prob > 0.5).astype(int).flatten()

    print("\n=== INDEPENDENT TEST RESULTS (flow_from_directory preprocessing) ===")
    print(classification_report(y_true, y_pred, target_names=["fake", "genuine"]))
    print("Confusion matrix (rows=actual [fake, genuine], cols=predicted [fake, genuine]):")
    print(confusion_matrix(y_true, y_pred))

    acc = (y_true == y_pred).mean()
    print(f"\nOverall test accuracy: {acc*100:.2f}%")

    print("\n--- Diagnosis ---")
    if acc >= 0.90:
        print("Accuracy is close to your validation number.")
        print("=> This CONFIRMS the earlier '50% test' result was a preprocessing")
        print("   mismatch in the old test script, NOT a real generalization problem.")
        print("=> Do not switch to transfer learning. Fix predict.py/api.py to use this")
        print("   same flow_from_directory-style preprocessing and move to Android integration.")
    elif acc <= 0.60:
        print("Accuracy is still low and/or one-sided.")
        print("=> This is a genuine generalization problem, not preprocessing.")
        print("=> Next step: transfer learning (MobileNetV2) + more diverse training data,")
        print("   as planned in Phase 2.")
    else:
        print("Accuracy is in between — partial improvement.")
        print("=> Preprocessing was likely PART of the problem. Re-check that predict.py")
        print("   and any other evaluation script use identical preprocessing to train.py")
        print("   before deciding whether transfer learning is still needed.")


if __name__ == "__main__":
    main()
