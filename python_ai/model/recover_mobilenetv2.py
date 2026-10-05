import os
import json
import shutil
import h5py
import numpy as np
import tensorflow as tf
from PIL import Image

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

ORIGINAL_H5 = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2.h5")
BACKUP_H5 = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2_original_backup.h5")
FIXED_H5 = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2_fixed.h5")
FIXED_KERAS = os.path.join(BASE_DIR, "trueproof_model_mobilenetv2_fixed.keras")

TEST_DIR = os.path.join(os.path.dirname(BASE_DIR), "dataset", "test")

IMG_SIZE = (224, 224)
THRESHOLD = 0.5


class MobileNetDivide(tf.keras.layers.Layer):
    def call(self, inputs):
        return tf.math.divide(
            inputs,
            tf.constant(127.5, dtype=tf.float32)
        )


class MobileNetSubtract(tf.keras.layers.Layer):
    def call(self, inputs):
        return tf.math.subtract(
            inputs,
            tf.constant(1.0, dtype=tf.float32)
        )


def backup_original():
    if not os.path.exists(BACKUP_H5):
        shutil.copy2(ORIGINAL_H5, BACKUP_H5)
        print("BACKUP CREATED:")
        print(BACKUP_H5)
    else:
        print("BACKUP ALREADY EXISTS")


def _strip_constant_args(layer):
    """Keeps only the first positional call-arg (the actual tensor
    reference) for every inbound node of this layer -- drops the raw
    constant (127.5 or 1.0) that Keras 3 can't reconstruct."""
    inbound = layer.get("inbound_nodes", [])
    for node in inbound:
        if "args" in node and len(node["args"]) > 0:
            node["args"] = [node["args"][0]]


def patch_model_config():
    print()
    print("PATCHING KERAS MODEL CONFIG...")

    shutil.copy2(ORIGINAL_H5, FIXED_H5)

    with h5py.File(FIXED_H5, "r+") as f:
        if "model_config" not in f.attrs:
            raise RuntimeError("model_config not found in H5")

        config = json.loads(f.attrs["model_config"])
        all_layers = config["config"]["layers"]

        # ----------------------------------------------------------
        # ROBUSTNESS FIX: don't only trust the guessed layer NAME
        # ("true_divide" / "subtract") -- match by class_name first,
        # since that's what the earlier errors ("Unknown layer:
        # TrueDivide") actually confirmed exists in the file. If a
        # name-based match also happens to work, great; either way we
        # print every layer's name+class_name so a wrong guess is
        # immediately visible instead of causing another blind retry.
        # ----------------------------------------------------------
        print()
        print("All top-level layers found in model_config:")
        for layer in all_layers:
            print(f"    name={layer.get('name')!r:30s} class_name={layer.get('class_name')!r}")

        divide_layer = None
        subtract_layer = None

        for layer in all_layers:
            class_name = layer.get("class_name")

            # Stricter match: we already confirmed via the earlier
            # "Unknown layer: TrueDivide" error that the H5 genuinely
            # contains a layer with this exact class_name, so match on
            # class_name alone -- no name-based fallback needed here.
            if class_name == "TrueDivide":
                divide_layer = layer

            if class_name == "Subtract":
                # Real Keras also has a built-in merge layer called
                # "Subtract" that takes two tensors and no constants.
                # We only want the auto-generated preprocessing one,
                # which has a raw number (not a tensor ref) as its 2nd
                # positional arg.
                inbound = layer.get("inbound_nodes", [])
                has_constant_arg = any(
                    len(node.get("args", [])) > 1
                    and isinstance(node["args"][1], (int, float))
                    for node in inbound
                )
                if has_constant_arg:
                    subtract_layer = layer

        if divide_layer is None:
            raise RuntimeError(
                "Could not find a TrueDivide layer by class_name or name. "
                "See the layer list printed above and report back with it."
            )
        if subtract_layer is None:
            raise RuntimeError(
                "Could not find a Subtract layer by class_name or name. "
                "See the layer list printed above and report back with it."
            )

        divide_layer["class_name"] = "MobileNetDivide"
        divide_layer["registered_name"] = "MobileNetDivide"
        _strip_constant_args(divide_layer)

        subtract_layer["class_name"] = "MobileNetSubtract"
        subtract_layer["registered_name"] = "MobileNetSubtract"
        _strip_constant_args(subtract_layer)

        f.attrs["model_config"] = json.dumps(config)

    print()
    print(f"TrueDivide (name={divide_layer.get('name')!r}) -> MobileNetDivide : PATCHED")
    print(f"Subtract   (name={subtract_layer.get('name')!r}) -> MobileNetSubtract: PATCHED")
    print("FIXED H5:")
    print(FIXED_H5)


def load_fixed_model():
    print()
    print("LOADING FIXED MODEL...")

    model = tf.keras.models.load_model(
        FIXED_H5,
        compile=False,
        custom_objects={
            "MobileNetDivide": MobileNetDivide,
            "MobileNetSubtract": MobileNetSubtract,
        },
    )

    print("LOAD_MODEL_SUCCESS")
    print("Input :", model.input_shape)
    print("Output:", model.output_shape)

    # Verify important trained layers.
    dense = model.get_layer("dense")
    output = model.get_layer("genuine_probability")

    print("Dense kernel shape :", dense.kernel.shape)
    print("Output kernel shape:", output.kernel.shape)

    base = model.get_layer("mobilenetv2_1.00_224")
    conv1 = base.get_layer("Conv1")

    print("MobileNet Conv1 shape:", conv1.kernel.shape)

    return model


def predict_image(model, path):
    img = Image.open(path).convert("RGB")
    img = img.resize(IMG_SIZE, Image.Resampling.BILINEAR)

    arr = np.asarray(img, dtype=np.float32)
    arr = np.expand_dims(arr, axis=0)

    probability = float(model.predict(arr, verbose=0)[0][0])

    return probability


def evaluate_test(model):
    print()
    print("=" * 60)
    print("MOBILENETV2 TEST EVALUATION")
    print("=" * 60)

    if not os.path.isdir(TEST_DIR):
        raise RuntimeError("Test directory not found: " + TEST_DIR)

    results = []

    for class_name, true_label in [
        ("fake", 0),
        ("genuine", 1),
    ]:
        class_dir = os.path.join(TEST_DIR, class_name)

        if not os.path.isdir(class_dir):
            raise RuntimeError("Missing test class: " + class_dir)

        for root, _, files in os.walk(class_dir):
            for filename in files:
                if not filename.lower().endswith(
                    (".png", ".jpg", ".jpeg", ".webp", ".bmp")
                ):
                    continue

                path = os.path.join(root, filename)

                try:
                    probability = predict_image(model, path)
                    predicted_label = 1 if probability >= THRESHOLD else 0

                    results.append(
                        (
                            true_label,
                            predicted_label,
                            probability,
                            path,
                        )
                    )

                except Exception as e:
                    print("ERROR:", path)
                    print(e)
                    raise

    total = len(results)

    if total == 0:
        raise RuntimeError("No test images found")

    # Exact test-count check -- prevents accidentally evaluating the
    # wrong dataset (partial split, stale folder, etc.).
    fake_count = sum(1 for y, _, _, _ in results if y == 0)
    genuine_count = sum(1 for y, _, _, _ in results if y == 1)

    if total != 328 or fake_count != 164 or genuine_count != 164:
        raise RuntimeError(
            f"Unexpected test set: total={total}, "
            f"fake={fake_count}, genuine={genuine_count}"
        )

    tp = sum(
        1 for y, p, _, _ in results
        if y == 1 and p == 1
    )

    tn = sum(
        1 for y, p, _, _ in results
        if y == 0 and p == 0
    )

    fp = sum(
        1 for y, p, _, _ in results
        if y == 0 and p == 1
    )

    fn = sum(
        1 for y, p, _, _ in results
        if y == 1 and p == 0
    )

    accuracy = (tp + tn) / total

    fake_precision = tn / (tn + fn) if (tn + fn) else 0.0
    fake_recall = tn / (tn + fp) if (tn + fp) else 0.0

    genuine_precision = tp / (tp + fp) if (tp + fp) else 0.0
    genuine_recall = tp / (tp + fn) if (tp + fn) else 0.0

    fake_f1 = (
        2 * fake_precision * fake_recall /
        (fake_precision + fake_recall)
        if (fake_precision + fake_recall)
        else 0.0
    )

    genuine_f1 = (
        2 * genuine_precision * genuine_recall /
        (genuine_precision + genuine_recall)
        if (genuine_precision + genuine_recall)
        else 0.0
    )

    print()
    print("Total test images :", total)
    print()
    print("CONFUSION MATRIX")
    print("                 Pred EDITED   Pred ORIGINAL")
    print("True EDITED      ", tn, "           ", fp)
    print("True ORIGINAL    ", fn, "           ", tp)

    print()
    print("Accuracy :", f"{accuracy * 100:.2f}%")

    print()
    print("EDITED / FAKE")
    print("Precision:", f"{fake_precision * 100:.2f}%")
    print("Recall   :", f"{fake_recall * 100:.2f}%")
    print("F1       :", f"{fake_f1 * 100:.2f}%")

    print()
    print("ORIGINAL / GENUINE")
    print("Precision:", f"{genuine_precision * 100:.2f}%")
    print("Recall   :", f"{genuine_recall * 100:.2f}%")
    print("F1       :", f"{genuine_f1 * 100:.2f}%")

    fake_probs = [
        probability
        for y, _, probability, _ in results
        if y == 0
    ]

    genuine_probs = [
        probability
        for y, _, probability, _ in results
        if y == 1
    ]

    print()
    print("P(GENUINE) statistics")
    print(
        "EDITED   mean:",
        f"{np.mean(fake_probs):.6f}",
        "min:",
        f"{np.min(fake_probs):.6f}",
        "max:",
        f"{np.max(fake_probs):.6f}",
    )

    print(
        "ORIGINAL mean:",
        f"{np.mean(genuine_probs):.6f}",
        "min:",
        f"{np.min(genuine_probs):.6f}",
        "max:",
        f"{np.max(genuine_probs):.6f}",
    )

    print()
    print("Previous custom CNN test accuracy: 97.26%")

    if accuracy > 0.9726:
        print("RESULT: MobileNetV2 BEATS custom CNN")
    elif accuracy == 0.9726:
        print("RESULT: MobileNetV2 MATCHES custom CNN")
    else:
        print("RESULT: custom CNN is currently BETTER")

    return accuracy


def save_keras_model(model):
    print()
    print("SAVING FIXED KERAS MODEL...")

    model.save(FIXED_KERAS)

    print("SAVED:")
    print(FIXED_KERAS)


def main():
    print("=" * 60)
    print("TrueProof MobileNetV2 H5 Recovery")
    print("=" * 60)

    print("TensorFlow:", tf.__version__)
    print("Keras     :", tf.keras.__version__)

    if not os.path.exists(ORIGINAL_H5):
        raise RuntimeError(
            "Original MobileNetV2 H5 not found:\n"
            + ORIGINAL_H5
        )

    backup_original()
    patch_model_config()

    model = load_fixed_model()

    accuracy = evaluate_test(model)

    save_keras_model(model)

    print()
    print("=" * 60)
    print("RECOVERY COMPLETE")
    print("=" * 60)
    print("MobileNetV2 test accuracy:", f"{accuracy * 100:.2f}%")
    print()
    print("Original H5 kept safely.")
    print("Fixed H5 :", FIXED_H5)
    print("Fixed Keras:", FIXED_KERAS)


if __name__ == "__main__":
    main()