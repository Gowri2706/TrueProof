import os
import sys
import json
import numpy as np
import tensorflow as tf
from PIL import Image

# ============================================================
# TrueProof - Custom CNN Model Behavior Audit
# ============================================================
# Purpose:
#   Audit the CURRENT production candidate model without
#   retraining or modifying the model.
#
# Labels:
#   0 = EDITED / FAKE
#   1 = ORIGINAL / GENUINE
#
# Model output:
#   P(GENUINE)
#
# Test dataset expected:
#   dataset/test/fake/...
#   dataset/test/genuine/...
#
# This script checks:
#   1. Overall test accuracy
#   2. Confusion matrix
#   3. Precision / recall / F1
#   4. Per-app accuracy
#   5. False positives / false negatives
#   6. High-confidence wrong predictions
#   7. Low-confidence correct predictions
#   8. Borderline predictions
#   9. Confidence distribution
#  10. Worst examples
#
# IMPORTANT:
#   This script does NOT train, save, modify, or overwrite the model.
# ============================================================

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODEL_PATH = os.path.join(
    BASE_DIR,
    "model",
    "trueproof_model.h5"
)

TEST_DIR = os.path.join(
    BASE_DIR,
    "dataset",
    "test"
)

REPORT_PATH = os.path.join(
    BASE_DIR,
    "model",
    "audit_model_behavior_report.txt"
)

IMG_SIZE = (224, 224)

# ------------------------------------------------------------
# Thresholds
# ------------------------------------------------------------

DECISION_THRESHOLD = 0.50

# Probability close to 0.50 = uncertain
BORDERLINE_LOW = 0.45
BORDERLINE_HIGH = 0.55

# Correct but low-confidence
LOW_CONFIDENCE_CORRECT = 0.60

# Wrong but model was very confident
HIGH_CONFIDENCE_WRONG = 0.80


# ============================================================
# Utility
# ============================================================

def print_line(text=""):
    print(text)


def get_app_name(path):
    """
    Expected structure:

        dataset/test/genuine/Gpay/file.png
        dataset/test/fake/Phonepe/file.png

    Returns:
        Gpay / Phonepe / Paytm / UNKNOWN
    """

    parts = os.path.normpath(path).split(os.sep)

    if len(parts) >= 3:
        # app folder is parent folder of filename
        return parts[-2]

    return "UNKNOWN"


def get_all_images(root_dir):
    """
    Recursively collect image files.
    """
    valid_extensions = {
        ".png",
        ".jpg",
        ".jpeg",
        ".bmp",
        ".webp"
    }

    files = []

    if not os.path.isdir(root_dir):
        return files

    for root, dirs, filenames in os.walk(root_dir):
        for filename in filenames:
            ext = os.path.splitext(filename)[1].lower()

            if ext in valid_extensions:
                files.append(os.path.join(root, filename))

    files.sort()

    return files


def load_image(path):
    """
    IMPORTANT:
    This matches the existing Custom CNN preprocessing:

        RGB
        resize 224x224
        raw 0-255 float32

    The model contains its own Rescaling layer.
    """

    img = Image.open(path).convert("RGB")
    img = img.resize(IMG_SIZE, Image.Resampling.NEAREST)

    arr = np.asarray(img, dtype=np.float32)

    return arr


def get_true_label(path):
    """
    fake = 0
    genuine = 1
    """

    normalized = os.path.normpath(path).lower()
    parts = normalized.split(os.sep)

    if "fake" in parts:
        return 0

    if "genuine" in parts:
        return 1

    return None


def label_name(label):
    if label == 0:
        return "EDITED"
    if label == 1:
        return "ORIGINAL"
    return "UNKNOWN"


def predicted_label(probability):
    if probability >= DECISION_THRESHOLD:
        return 1

    return 0


def confidence_from_probability(probability):
    """
    Confidence of the predicted class.
    """

    if probability >= 0.50:
        return probability

    return 1.0 - probability


def format_prediction(item):
    return (
        f"{item['app']:<10} | "
        f"{item['true_label']:<8} | "
        f"{item['pred_label']:<8} | "
        f"P(genuine)={item['probability_genuine']:.6f} | "
        f"confidence={item['confidence'] * 100:6.2f}% | "
        f"{item['filename']}"
    )


# ============================================================
# Main
# ============================================================

def main():

    print("=" * 90)
    print("TrueProof - Custom CNN Model Behavior Audit")
    print("=" * 90)

    print()
    print(f"BASE_DIR   : {BASE_DIR}")
    print(f"MODEL_PATH : {MODEL_PATH}")
    print(f"TEST_DIR   : {TEST_DIR}")
    print()

    # --------------------------------------------------------
    # 1. Check model
    # --------------------------------------------------------

    if not os.path.isfile(MODEL_PATH):
        print("ERROR: Model file not found.")
        print(MODEL_PATH)
        sys.exit(1)

    print("Loading model...")

    try:
        model = tf.keras.models.load_model(MODEL_PATH)
    except Exception as exc:
        print()
        print("ERROR: Could not load model.")
        print(str(exc))
        sys.exit(1)

    print("MODEL LOAD: PASS")

    print()
    print("Model input shape :", model.input_shape)
    print("Model output shape:", model.output_shape)

    # --------------------------------------------------------
    # 2. Check test directory
    # --------------------------------------------------------

    if not os.path.isdir(TEST_DIR):
        print()
        print("ERROR: Test directory not found.")
        print(TEST_DIR)
        sys.exit(1)

    fake_dir = os.path.join(TEST_DIR, "fake")
    genuine_dir = os.path.join(TEST_DIR, "genuine")

    fake_files = get_all_images(fake_dir)
    genuine_files = get_all_images(genuine_dir)

    print()
    print("TEST DATASET")
    print("-" * 90)

    print(f"EDITED / fake     : {len(fake_files)}")
    print(f"ORIGINAL / genuine: {len(genuine_files)}")
    print(f"TOTAL              : {len(fake_files) + len(genuine_files)}")

    if len(fake_files) == 0 or len(genuine_files) == 0:
        print()
        print("ERROR: Missing fake or genuine test images.")
        sys.exit(1)

    # --------------------------------------------------------
    # 3. Build list
    # --------------------------------------------------------

    all_files = []

    for path in fake_files:
        all_files.append((path, 0))

    for path in genuine_files:
        all_files.append((path, 1))

    all_files.sort(key=lambda x: x[0].lower())

    # --------------------------------------------------------
    # 4. Prediction
    # --------------------------------------------------------

    print()
    print("Running predictions...")
    print("-" * 90)

    results = []

    for index, (path, true_label) in enumerate(all_files, start=1):

        try:
            img = load_image(path)

            batch = np.expand_dims(img, axis=0)

            output = model.predict(
                batch,
                verbose=0
            )

            probability = float(np.asarray(output).reshape(-1)[0])

            pred_label = predicted_label(probability)

            confidence = confidence_from_probability(
                probability
            )

            app = get_app_name(path)

            item = {
                "path": path,
                "filename": os.path.basename(path),
                "app": app,
                "true_label_id": true_label,
                "true_label": label_name(true_label),
                "pred_label_id": pred_label,
                "pred_label": label_name(pred_label),
                "probability_genuine": probability,
                "confidence": confidence,
                "correct": pred_label == true_label
            }

            results.append(item)

        except Exception as exc:
            print()
            print("WARNING: Prediction failed")
            print(path)
            print(str(exc))

    print()
    print(f"Predictions completed: {len(results)}")

    if not results:
        print("ERROR: No predictions produced.")
        sys.exit(1)

    # --------------------------------------------------------
    # 5. Overall metrics
    # --------------------------------------------------------

    total = len(results)

    correct = sum(
        1 for x in results
        if x["correct"]
    )

    incorrect = total - correct

    accuracy = correct / total

    tp = sum(
        1 for x in results
        if x["true_label_id"] == 1
        and x["pred_label_id"] == 1
    )

    tn = sum(
        1 for x in results
        if x["true_label_id"] == 0
        and x["pred_label_id"] == 0
    )

    fp = sum(
        1 for x in results
        if x["true_label_id"] == 0
        and x["pred_label_id"] == 1
    )

    fn = sum(
        1 for x in results
        if x["true_label_id"] == 1
        and x["pred_label_id"] == 0
    )

    # --------------------------------------------------------
    # Metrics
    # --------------------------------------------------------

    edited_precision = (
        tn / (tn + fn)
        if (tn + fn) > 0
        else 0.0
    )

    edited_recall = (
        tn / (tn + fp)
        if (tn + fp) > 0
        else 0.0
    )

    edited_f1 = (
        2 * edited_precision * edited_recall
        / (edited_precision + edited_recall)
        if (edited_precision + edited_recall) > 0
        else 0.0
    )

    original_precision = (
        tp / (tp + fp)
        if (tp + fp) > 0
        else 0.0
    )

    original_recall = (
        tp / (tp + fn)
        if (tp + fn) > 0
        else 0.0
    )

    original_f1 = (
        2 * original_precision * original_recall
        / (original_precision + original_recall)
        if (original_precision + original_recall) > 0
        else 0.0
    )

    # --------------------------------------------------------
    # Confidence statistics
    # --------------------------------------------------------

    probabilities = np.array(
        [x["probability_genuine"] for x in results],
        dtype=np.float32
    )

    confidences = np.array(
        [x["confidence"] for x in results],
        dtype=np.float32
    )

    correct_confidences = np.array(
        [
            x["confidence"]
            for x in results
            if x["correct"]
        ],
        dtype=np.float32
    )

    wrong_confidences = np.array(
        [
            x["confidence"]
            for x in results
            if not x["correct"]
        ],
        dtype=np.float32
    )

    # --------------------------------------------------------
    # 6. Per-app metrics
    # --------------------------------------------------------

    apps = sorted(
        set(x["app"] for x in results)
    )

    app_metrics = {}

    for app in apps:

        app_items = [
            x for x in results
            if x["app"] == app
        ]

        app_total = len(app_items)

        app_correct = sum(
            1 for x in app_items
            if x["correct"]
        )

        app_accuracy = (
            app_correct / app_total
            if app_total > 0
            else 0.0
        )

        app_original = [
            x for x in app_items
            if x["true_label_id"] == 1
        ]

        app_edited = [
            x for x in app_items
            if x["true_label_id"] == 0
        ]

        original_correct = sum(
            1 for x in app_original
            if x["correct"]
        )

        edited_correct = sum(
            1 for x in app_edited
            if x["correct"]
        )

        original_accuracy = (
            original_correct / len(app_original)
            if app_original
            else 0.0
        )

        edited_accuracy = (
            edited_correct / len(app_edited)
            if app_edited
            else 0.0
        )

        app_conf = np.array(
            [x["confidence"] for x in app_items],
            dtype=np.float32
        )

        app_metrics[app] = {
            "total": app_total,
            "correct": app_correct,
            "accuracy": app_accuracy,
            "original_total": len(app_original),
            "original_accuracy": original_accuracy,
            "edited_total": len(app_edited),
            "edited_accuracy": edited_accuracy,
            "mean_confidence": float(np.mean(app_conf))
        }

    # --------------------------------------------------------
    # 7. Error categories
    # --------------------------------------------------------

    wrong = [
        x for x in results
        if not x["correct"]
    ]

    false_positive = [
        x for x in results
        if x["true_label_id"] == 0
        and x["pred_label_id"] == 1
    ]

    false_negative = [
        x for x in results
        if x["true_label_id"] == 1
        and x["pred_label_id"] == 0
    ]

    high_conf_wrong = sorted(
        wrong,
        key=lambda x: x["confidence"],
        reverse=True
    )

    high_conf_wrong = [
        x for x in high_conf_wrong
        if x["confidence"] >= HIGH_CONFIDENCE_WRONG
    ]

    low_conf_correct = sorted(
        [
            x for x in results
            if x["correct"]
            and x["confidence"] < LOW_CONFIDENCE_CORRECT
        ],
        key=lambda x: x["confidence"]
    )

    borderline = sorted(
        [
            x for x in results
            if BORDERLINE_LOW
            <= x["probability_genuine"]
            <= BORDERLINE_HIGH
        ],
        key=lambda x: abs(
            x["probability_genuine"] - 0.50
        )
    )

    # --------------------------------------------------------
    # 8. Worst predictions
    # --------------------------------------------------------

    worst_original = sorted(
        [
            x for x in results
            if x["true_label_id"] == 1
        ],
        key=lambda x: x["probability_genuine"]
    )

    worst_edited = sorted(
        [
            x for x in results
            if x["true_label_id"] == 0
        ],
        key=lambda x: x["probability_genuine"],
        reverse=True
    )

    # --------------------------------------------------------
    # 9. Print report
    # --------------------------------------------------------

    lines = []

    def report(text=""):
        lines.append(text)
        print(text)

    report()
    report("=" * 90)
    report("TRUEPROOF - CUSTOM CNN MODEL BEHAVIOR AUDIT REPORT")
    report("=" * 90)

    report()
    report("MODEL")
    report("-" * 90)
    report(f"Model path : {MODEL_PATH}")
    report(f"Input shape: {model.input_shape}")
    report(f"Output shape: {model.output_shape}")
    report("Input preprocessing: RGB -> 224x224 -> raw 0-255 float32")
    report("Output meaning: P(GENUINE)")
    report("Decision threshold: 0.50")

    report()
    report("OVERALL TEST RESULTS")
    report("-" * 90)
    report(f"Total images : {total}")
    report(f"Correct      : {correct}")
    report(f"Incorrect    : {incorrect}")
    report(f"Accuracy     : {accuracy * 100:.2f}%")

    report()
    report("CONFUSION MATRIX")
    report("-" * 90)
    report("                    Pred EDITED    Pred ORIGINAL")
    report(f"True EDITED          {tn:5d}             {fp:5d}")
    report(f"True ORIGINAL        {fn:5d}             {tp:5d}")

    report()
    report("CLASS METRICS")
    report("-" * 90)
    report(
        f"EDITED   precision={edited_precision * 100:.2f}%  "
        f"recall={edited_recall * 100:.2f}%  "
        f"F1={edited_f1 * 100:.2f}%"
    )

    report(
        f"ORIGINAL precision={original_precision * 100:.2f}%  "
        f"recall={original_recall * 100:.2f}%  "
        f"F1={original_f1 * 100:.2f}%"
    )

    report()
    report("PER-APP RESULTS")
    report("-" * 90)

    for app in apps:

        m = app_metrics[app]

        report(
            f"{app:<10} "
            f"total={m['total']:3d}  "
            f"accuracy={m['accuracy'] * 100:6.2f}%  "
            f"edited={m['edited_accuracy'] * 100:6.2f}%  "
            f"original={m['original_accuracy'] * 100:6.2f}%  "
            f"mean_conf={m['mean_confidence'] * 100:6.2f}%"
        )

    report()
    report("CONFIDENCE / PROBABILITY")
    report("-" * 90)

    report(
        f"P(genuine) mean : {np.mean(probabilities):.6f}"
    )

    report(
        f"P(genuine) min  : {np.min(probabilities):.6f}"
    )

    report(
        f"P(genuine) max  : {np.max(probabilities):.6f}"
    )

    report(
        f"P(genuine) std  : {np.std(probabilities):.6f}"
    )

    report(
        f"Confidence mean  : {np.mean(confidences) * 100:.2f}%"
    )

    report(
        f"Confidence min   : {np.min(confidences) * 100:.2f}%"
    )

    report(
        f"Confidence max   : {np.max(confidences) * 100:.2f}%"
    )

    if len(correct_confidences) > 0:
        report(
            f"Correct mean conf: "
            f"{np.mean(correct_confidences) * 100:.2f}%"
        )

    if len(wrong_confidences) > 0:
        report(
            f"Wrong mean conf  : "
            f"{np.mean(wrong_confidences) * 100:.2f}%"
        )

    report()
    report("ERROR COUNTS")
    report("-" * 90)

    report(
        f"False EDITED -> ORIGINAL : {fp}"
    )

    report(
        f"False ORIGINAL -> EDITED : {fn}"
    )

    report(
        f"High-confidence wrong >= "
        f"{HIGH_CONFIDENCE_WRONG * 100:.0f}% : "
        f"{len(high_conf_wrong)}"
    )

    report(
        f"Low-confidence correct < "
        f"{LOW_CONFIDENCE_CORRECT * 100:.0f}% : "
        f"{len(low_conf_correct)}"
    )

    report(
        f"Borderline "
        f"{BORDERLINE_LOW:.2f} - {BORDERLINE_HIGH:.2f} : "
        f"{len(borderline)}"
    )

    # --------------------------------------------------------
    # 10. False positives
    # --------------------------------------------------------

    report()
    report("FALSE POSITIVES")
    report("-" * 90)
    report("True = EDITED, Predicted = ORIGINAL")

    if not false_positive:
        report("None")
    else:
        for item in sorted(
            false_positive,
            key=lambda x: x["probability_genuine"],
            reverse=True
        ):
            report(format_prediction(item))

    # --------------------------------------------------------
    # 11. False negatives
    # --------------------------------------------------------

    report()
    report("FALSE NEGATIVES")
    report("-" * 90)
    report("True = ORIGINAL, Predicted = EDITED")

    if not false_negative:
        report("None")
    else:
        for item in sorted(
            false_negative,
            key=lambda x: x["probability_genuine"]
        ):
            report(format_prediction(item))

    # --------------------------------------------------------
    # 12. High confidence wrong
    # --------------------------------------------------------

    report()
    report(
        f"HIGH-CONFIDENCE WRONG PREDICTIONS "
        f"(>= {HIGH_CONFIDENCE_WRONG * 100:.0f}%)"
    )
    report("-" * 90)

    if not high_conf_wrong:
        report("None")
    else:
        for item in high_conf_wrong[:30]:
            report(format_prediction(item))

    # --------------------------------------------------------
    # 13. Low confidence correct
    # --------------------------------------------------------

    report()
    report(
        f"LOW-CONFIDENCE CORRECT PREDICTIONS "
        f"(< {LOW_CONFIDENCE_CORRECT * 100:.0f}%)"
    )
    report("-" * 90)

    if not low_conf_correct:
        report("None")
    else:
        for item in low_conf_correct[:30]:
            report(format_prediction(item))

    # --------------------------------------------------------
    # 14. Borderline
    # --------------------------------------------------------

    report()
    report(
        f"BORDERLINE PREDICTIONS "
        f"({BORDERLINE_LOW:.2f} <= P(genuine) <= "
        f"{BORDERLINE_HIGH:.2f})"
    )
    report("-" * 90)

    if not borderline:
        report("None")
    else:
        for item in borderline[:40]:
            report(format_prediction(item))

    # --------------------------------------------------------
    # 15. Worst genuine
    # --------------------------------------------------------

    report()
    report("WORST ORIGINAL / GENUINE EXAMPLES")
    report("-" * 90)
    report(
        "Lowest P(genuine) among true ORIGINAL images"
    )

    for item in worst_original[:20]:
        report(format_prediction(item))

    # --------------------------------------------------------
    # 16. Worst edited
    # --------------------------------------------------------

    report()
    report("WORST EDITED / FAKE EXAMPLES")
    report("-" * 90)
    report(
        "Highest P(genuine) among true EDITED images"
    )

    for item in worst_edited[:20]:
        report(format_prediction(item))

    # --------------------------------------------------------
    # 17. App-specific errors
    # --------------------------------------------------------

    report()
    report("APP-SPECIFIC ERROR SUMMARY")
    report("-" * 90)

    for app in apps:

        app_items = [
            x for x in results
            if x["app"] == app
        ]

        app_fp = sum(
            1 for x in app_items
            if x["true_label_id"] == 0
            and x["pred_label_id"] == 1
        )

        app_fn = sum(
            1 for x in app_items
            if x["true_label_id"] == 1
            and x["pred_label_id"] == 0
        )

        report(
            f"{app:<10} "
            f"FP={app_fp:3d}  "
            f"FN={app_fn:3d}"
        )

    # --------------------------------------------------------
    # 18. Interpretation
    # --------------------------------------------------------

    report()
    report("AUDIT INTERPRETATION")
    report("-" * 90)

    if accuracy >= 0.95:
        report(
            "Overall accuracy is strong (>=95%)."
        )
    elif accuracy >= 0.90:
        report(
            "Overall accuracy is good (90-95%) but "
            "error analysis is still important."
        )
    else:
        report(
            "Overall accuracy is below 90%; "
            "model improvement should be investigated."
        )

    if len(high_conf_wrong) == 0:
        report(
            "No high-confidence wrong predictions found."
        )
    else:
        report(
            f"{len(high_conf_wrong)} high-confidence wrong "
            "prediction(s) need investigation."
        )

    if len(borderline) > 0:
        report(
            f"{len(borderline)} borderline prediction(s) "
            "are close to the decision boundary."
        )
    else:
        report(
            "No borderline predictions in the selected range."
        )

    if len(low_conf_correct) > 0:
        report(
            f"{len(low_conf_correct)} correct prediction(s) "
            "have confidence below 60%; these are candidates "
            "for confidence/calibration investigation."
        )

    # Check app spread

    if app_metrics:

        app_accuracies = [
            m["accuracy"]
            for m in app_metrics.values()
        ]

        spread = max(app_accuracies) - min(app_accuracies)

        report(
            f"Per-app accuracy spread: {spread * 100:.2f} percentage points."
        )

        if spread >= 0.10:
            report(
                "WARNING: Large per-app performance difference detected. "
                "The model may be learning app-specific patterns."
            )
        else:
            report(
                "Per-app performance is relatively consistent."
            )

    # --------------------------------------------------------
    # 19. Save JSON data
    # --------------------------------------------------------

    json_path = os.path.join(
        BASE_DIR,
        "model",
        "audit_model_behavior_results.json"
    )

    json_payload = {
        "model_path": MODEL_PATH,
        "test_dir": TEST_DIR,
        "threshold": DECISION_THRESHOLD,
        "total": total,
        "correct": correct,
        "incorrect": incorrect,
        "accuracy": accuracy,
        "confusion_matrix": {
            "true_edited_pred_edited": tn,
            "true_edited_pred_original": fp,
            "true_original_pred_edited": fn,
            "true_original_pred_original": tp
        },
        "metrics": {
            "edited_precision": edited_precision,
            "edited_recall": edited_recall,
            "edited_f1": edited_f1,
            "original_precision": original_precision,
            "original_recall": original_recall,
            "original_f1": original_f1
        },
        "confidence": {
            "mean": float(np.mean(confidences)),
            "min": float(np.min(confidences)),
            "max": float(np.max(confidences)),
            "std": float(np.std(confidences))
        },
        "counts": {
            "false_positive": len(false_positive),
            "false_negative": len(false_negative),
            "high_confidence_wrong": len(high_conf_wrong),
            "low_confidence_correct": len(low_conf_correct),
            "borderline": len(borderline)
        },
        "app_metrics": app_metrics,
        "predictions": results
    }

    try:
        with open(
            json_path,
            "w",
            encoding="utf-8"
        ) as f:
            json.dump(
                json_payload,
                f,
                indent=2,
                ensure_ascii=False
            )

        report()
        report(f"JSON report saved:")
        report(json_path)

    except Exception as exc:
        report()
        report("WARNING: Could not save JSON report.")
        report(str(exc))

    # --------------------------------------------------------
    # 20. Save text report
    # --------------------------------------------------------

    try:
        with open(
            REPORT_PATH,
            "w",
            encoding="utf-8"
        ) as f:
            f.write("\n".join(lines))

        print()
        print("=" * 90)
        print("AUDIT COMPLETE")
        print("=" * 90)
        print()
        print("Text report:")
        print(REPORT_PATH)
        print()
        print("JSON report:")
        print(json_path)
        print()

    except Exception as exc:
        print()
        print("WARNING: Could not save text report.")
        print(str(exc))


if __name__ == "__main__":
    main()