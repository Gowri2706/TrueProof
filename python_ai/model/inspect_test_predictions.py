"""
inspect_test_predictions.py
------------------------------
PURPOSE: before spending hours retraining with MobileNetV2, do a 2-minute
sanity check on WHY every single test/genuine image is being scored as
fake. There are two very different possible causes that look identical in
a confusion matrix but need completely different fixes:

  (A) The model has genuinely failed to generalize — it looks at a real
      genuine screenshot and, using learned (but non-transferable) visual
      cues, scores it low. Probabilities would look "confidently wrong"
      but still be varied and non-degenerate (e.g. spread across
      0.01-0.30) — a real, if bad, decision.

  (B) Something about the test/genuine images themselves is different
      from train/genuine in a way that has nothing to do with fraud
      detection — corrupt files, wrong image mode (RGBA/L instead of
      RGB), truncated files, a wrong folder, near-identical tiny/blank
      images, etc. In this case probabilities are usually EXTREME
      (e.g. ~0.0000001, all nearly identical) rather than "confidently
      wrong but varied" — that's the signature of the model being fed
      garbage input, not making a real (if bad) decision.

This script prints, for EVERY test image:
    - its predicted probability of "genuine" (not thresholded)
    - its image mode (RGB/RGBA/L/P), size, and file size
so you can immediately see which of (A) or (B) is happening, and whether
test/genuine images look structurally different from train/genuine ones.

USAGE (from python_ai/ folder):
    python model/inspect_test_predictions.py
"""

import os
import numpy as np
import tensorflow as tf
from PIL import Image

IMG_SIZE = (224, 224)
DATASET_DIR = os.path.join(os.path.dirname(__file__), "..", "dataset")
MODEL_PATH = os.path.join(os.path.dirname(__file__), "trueproof_model.h5")

try:
    RESAMPLE_NEAREST = Image.Resampling.NEAREST
except AttributeError:
    RESAMPLE_NEAREST = Image.NEAREST


def list_images(folder):
    exts = (".png", ".jpg", ".jpeg")
    paths = []

    # Folder + all subfolders (Gpay, Phonepe, Paytm)
    for root, _, files in os.walk(folder):
        for filename in files:
            if filename.lower().endswith(exts):
                paths.append(os.path.join(root, filename))

    return sorted(paths)


def describe_image(path):
    try:
        with Image.open(path) as im:
            mode = im.mode
            size = im.size
        file_size_kb = os.path.getsize(path) / 1024
        return mode, size, file_size_kb, None
    except Exception as e:
        return None, None, None, str(e)


def predict_prob(model, path):
    img = Image.open(path).convert("RGB").resize(IMG_SIZE, resample=RESAMPLE_NEAREST)
    arr = np.expand_dims(np.array(img, dtype=np.float32), axis=0)
    return float(model.predict(arr, verbose=0)[0][0])  # prob of GENUINE


def report_folder(model, folder, label, sample_limit=None):
    paths = list_images(folder)
    if sample_limit:
        paths = paths[:sample_limit]

    print(f"\n----- {label} ({len(paths)} images) -----")
    probs = []
    bad_files = 0
    for p in paths:
        mode, size, kb, err = describe_image(p)
        if err:
            print(f"  [CORRUPT/UNREADABLE] {os.path.basename(p)}: {err}")
            bad_files += 1
            continue
        prob = predict_prob(model, p)
        probs.append(prob)
        print(f"  {os.path.basename(p):35s} mode={mode:5s} size={str(size):12s} "
              f"{kb:7.1f}KB  P(genuine)={prob:.6f}")

    if probs:
        arr = np.array(probs)
        print(f"\n  Summary for {label}:")
        print(f"    mean P(genuine) = {arr.mean():.6f}")
        print(f"    min / max       = {arr.min():.6f} / {arr.max():.6f}")
        print(f"    std dev         = {arr.std():.6f}")
        if arr.std() < 0.01:
            print("    -> WARNING: almost zero variation across images.")
            print("       This looks like signature (B): the model is not really")
            print("       'looking' at each image differently — check for corrupt,")
            print("       near-duplicate, or wrongly-loaded files in this folder.")
    if bad_files:
        print(f"  {bad_files} unreadable/corrupt files found in {label}.")


def main():
    if not os.path.exists(MODEL_PATH):
        print(f"ERROR: model not found at {MODEL_PATH}. Run model/train.py first.")
        raise SystemExit(1)

    model = tf.keras.models.load_model(MODEL_PATH)

    # A) The problem folder: test/genuine — every one of these is being scored as fake.
    report_folder(model, os.path.join(DATASET_DIR, "test", "genuine"), "TEST / genuine")

    # B) For comparison: a handful of train/genuine images the model saw during training.
    #    If these look wildly different in mode/size/file-size from test/genuine, that
    #    difference itself may be the real bug.
    report_folder(model, os.path.join(DATASET_DIR, "train", "genuine"),
                  "TRAIN / genuine (first 15, for comparison)", sample_limit=15)

    # C) Also check test/fake for completeness — should score low P(genuine), which is correct.
    report_folder(model, os.path.join(DATASET_DIR, "test", "fake"), "TEST / fake")

    print("\n===================================================================")
    print("WHAT TO LOOK FOR:")
    print("1. Compare 'mode' and typical file size (KB) between TEST/genuine and")
    print("   TRAIN/genuine. If TEST/genuine images are RGBA/PNG-with-alpha or a very")
    print("   different resolution/file-size pattern than TRAIN/genuine, that mismatch")
    print("   itself (not model 'intelligence') is very likely the real bug.")
    print("2. If TEST/genuine's P(genuine) values are all extremely tiny (e.g. 1e-6 to")
    print("   1e-4) with near-zero std dev, the model is not making a nuanced wrong")
    print("   decision — it's being fed something structurally broken. Open 2-3 of")
    print("   those exact test/genuine files yourself in an image viewer and confirm")
    print("   they really are genuine, undamaged, correctly-oriented screenshots.")
    print("3. If P(genuine) values are spread out (e.g. 0.02 to 0.35) rather than all")
    print("   identical/extreme, that supports a real (if disappointing) generalization")
    print("   failure — THEN MobileNetV2 transfer learning is the right next step.")
    print("===================================================================")


if __name__ == "__main__":
    main()
