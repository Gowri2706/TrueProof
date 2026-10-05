"""
TrueProof - STRICT MobileNetV2 Verification + Test Evaluator

Semantics:
    0 = EDITED / FAKE
    1 = ORIGINAL / GENUINE

Model output:
    P(genuine)

This evaluator:
    1. Verifies the H5 file exists and is readable.
    2. Verifies the expected H5 structure.
    3. Rebuilds the EXACT training architecture.
    4. Manually loads H5 tensors layer-by-layer, matched BY NAME
       (kernel/bias/gamma/beta/moving_mean/moving_variance),
       never by raw positional order.
    5. Strictly verifies tensor counts.
    6. Strictly verifies tensor shapes.
    7. Strictly verifies loaded tensor values.
    8. Verifies class mapping JSON.
    9. Runs sanity predictions.
   10. Runs the complete 328-image test set.

IMPORTANT:
    - Do NOT use load_model().
    - Do NOT use model.load_weights().
    - Do NOT use skip_mismatch.
    - Do NOT silently skip missing layers/tensors.
"""

import os
import json
import sys
import h5py
import numpy as np
import tensorflow as tf

from tensorflow.keras import layers
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
    roc_auc_score,
)

# ============================================================
# PATHS
# ============================================================

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODEL_PATH = os.path.join(
    BASE_DIR,
    "model",
    "trueproof_model_mobilenetv2.h5",
)

CLASS_MAP_PATH = os.path.join(
    BASE_DIR,
    "model",
    "mobilenetv2_class_indices.json",
)

TEST_DIR = os.path.join(
    BASE_DIR,
    "dataset",
    "test",
)

# ============================================================
# SETTINGS
# ============================================================

IMG_SIZE = (224, 224)

CLASS_NAMES = ["fake", "genuine"]

LABEL_MAP = {
    "fake": 0,
    "genuine": 1,
}

THRESHOLD = 0.5

IMAGE_EXTENSIONS = {
    ".jpg",
    ".jpeg",
    ".png",
    ".bmp",
    ".webp",
}

# Known Keras weight base-name suffixes, in priority order.
# Used to identify which H5 dataset corresponds to which
# expected model weight, by NAME rather than by position.
KNOWN_WEIGHT_SUFFIXES = [
    "kernel",
    "bias",
    "gamma",
    "beta",
    "moving_mean",
    "moving_variance",
    "embeddings",
    "depthwise_kernel",
    "pointwise_kernel",
]

# ============================================================
# UTILS
# ============================================================

def fail(message):
    print()
    print("=" * 72)
    print("FAIL")
    print("=" * 72)
    print(message)
    print()
    sys.exit(1)


def pass_msg(message):
    print(f"[PASS] {message}")


def check(condition, message):
    if condition:
        pass_msg(message)
    else:
        fail(message)


def list_images(folder):
    result = []

    if not os.path.isdir(folder):
        return result

    for root, _, files in os.walk(folder):
        for filename in files:
            ext = os.path.splitext(filename)[1].lower()

            if ext in IMAGE_EXTENSIONS:
                result.append(
                    os.path.join(root, filename)
                )

    return sorted(result)


def load_image(path):
    image = tf.keras.utils.load_img(
        path,
        color_mode="rgb",
        target_size=IMG_SIZE,
        interpolation="nearest",
    )

    array = tf.keras.utils.img_to_array(image)

    # IMPORTANT:
    # Keep raw 0-255 input.
    # MobileNetV2 preprocess_input is inside the model.
    return array.astype(np.float32)


# ============================================================
# EXACT MODEL ARCHITECTURE
# ============================================================

def build_exact_model():
    """
    Rebuild the same architecture used by train_mobilenetv2.py.
    """

    inputs = layers.Input(
        shape=(224, 224, 3),
        name="input_image",
    )

    x = tf.keras.applications.mobilenet_v2.preprocess_input(
        inputs
    )

    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights=None,
        name="mobilenetv2_1.00_224",
    )

    x = base_model(
        x,
        training=False,
    )

    x = layers.GlobalAveragePooling2D(
        name="global_average_pooling2d"
    )(x)

    x = layers.Dropout(
        0.30,
        name="dropout",
    )(x)

    x = layers.Dense(
        128,
        activation="relu",
        name="dense",
    )(x)

    x = layers.Dropout(
        0.30,
        name="dropout_1",
    )(x)

    outputs = layers.Dense(
        1,
        activation="sigmoid",
        name="genuine_probability",
    )(x)

    model = tf.keras.Model(
        inputs=inputs,
        outputs=outputs,
        name="trueproof_mobilenetv2",
    )

    return model


# ============================================================
# H5 HELPERS
# ============================================================

def get_h5_weight_names(group):
    """
    Return all H5 datasets belonging to this layer.

    Some Keras H5 files store layer weights inside nested
    sub-groups, so we recursively collect datasets.

    NOTE: order here is NOT semantically meaningful (H5/traversal
    order does not necessarily match layer.weights order). Names
    are matched to model weights explicitly by suffix elsewhere —
    never by position.
    """

    found = []

    def visitor(name, obj):
        if isinstance(obj, h5py.Dataset):
            found.append(name)

    group.visititems(visitor)

    return found


def dataset_from_weight_name(group, weight_name):
    """
    Locate a nested H5 dataset by its relative path.
    """

    target = weight_name.lstrip("/")

    try:
        obj = group[target]
        if isinstance(obj, h5py.Dataset):
            return obj
    except Exception:
        pass

    matches = []

    def visitor(name, obj):
        if isinstance(obj, h5py.Dataset):
            clean_name = name.lstrip("/")

            if clean_name == target:
                matches.append(obj)

    group.visititems(visitor)

    if len(matches) == 1:
        return matches[0]

    return None


def collect_h5_groups(group, prefix=""):
    """
    Return every H5 group using its full path.
    """

    groups = {}

    for name, obj in group.items():

        if isinstance(obj, h5py.Group):

            full_path = (
                f"{prefix}/{name}"
                if prefix
                else name
            )

            groups[full_path] = obj

            groups.update(
                collect_h5_groups(
                    obj,
                    full_path,
                )
            )

    return groups


def find_unique_group_for_layer(
    layer,
    h5_groups,
):
    """
    Find the H5 group corresponding to a Keras layer.
    """

    layer_name = layer.name

    exact_candidates = []

    for path, group in h5_groups.items():
        basename = path.split("/")[-1]

        if basename == layer_name:
            exact_candidates.append(
                (path, group)
            )

    if len(exact_candidates) == 1:
        return exact_candidates[0]

    if len(exact_candidates) > 1:
        exact_candidates.sort(
            key=lambda item: item[0].count("/")
        )

        shortest_depth = exact_candidates[0][0].count("/")

        shortest = [
            item
            for item in exact_candidates
            if item[0].count("/") == shortest_depth
        ]

        if len(shortest) == 1:
            return shortest[0]

    return None


def weight_base_name(weight_var):
    """
    Extract the semantic base name of a Keras weight variable.

    e.g. "dense/kernel:0"  -> "kernel"
         "dense/bias:0"    -> "bias"
         "bn/gamma:0"      -> "gamma"

    This is what we match against H5 dataset names — NEVER
    positional order — because Dense/BatchNorm weight order in
    layer.weights ([kernel, bias] / [gamma, beta, moving_mean,
    moving_variance]) does not necessarily match the order H5
    datasets are returned in during traversal.
    """

    raw = weight_var.name

    last_segment = raw.split("/")[-1]

    if ":" in last_segment:
        last_segment = last_segment.split(":")[0]

    return last_segment


def h5_dataset_base_name(h5_name):
    """
    Extract the semantic base name of an H5 dataset path.

    e.g. "dense/dense/kernel:0" -> "kernel"
    """

    last_segment = h5_name.split("/")[-1]

    if ":" in last_segment:
        last_segment = last_segment.split(":")[0]

    return last_segment


def match_weights_by_name(layer, group, h5_weight_names):
    """
    Match each of the layer's expected weights (in layer.weights
    order) to the correct H5 dataset, by semantic base name
    (kernel/bias/gamma/beta/moving_mean/moving_variance/...),
    NOT by position.

    Returns a list of numpy arrays in the SAME order as
    layer.weights, or raises via fail() if any weight cannot be
    uniquely matched.
    """

    expected_weights = list(layer.weights)

    # Group H5 dataset names by their base name.
    by_base_name = {}

    for h5_name in h5_weight_names:
        base = h5_dataset_base_name(h5_name)
        by_base_name.setdefault(base, []).append(h5_name)

    arrays = []

    for weight_var in expected_weights:

        base = weight_base_name(weight_var)

        candidates = by_base_name.get(base, [])

        if len(candidates) == 0:
            fail(
                f"No H5 dataset found for layer "
                f"'{layer.name}' weight base name "
                f"'{base}' (weight variable: '{weight_var.name}').\n"
                f"Available H5 dataset names in this group: "
                f"{h5_weight_names}"
            )

        if len(candidates) > 1:
            # Multiple datasets share this base name (unexpected
            # for a single leaf layer's own group) — try exact
            # single-segment match "<base>:0" or "<base>" as a
            # tie-breaker; otherwise fail loudly rather than guess.
            tie_break = [
                name for name in candidates
                if name.split("/")[-1] in (base, f"{base}:0")
            ]

            if len(tie_break) == 1:
                candidates = tie_break
            else:
                fail(
                    f"Ambiguous H5 datasets for layer "
                    f"'{layer.name}' weight base name '{base}': "
                    f"{candidates}"
                )

        chosen_name = candidates[0]

        dataset = dataset_from_weight_name(group, chosen_name)

        if dataset is None:
            fail(
                f"Could not open matched H5 dataset "
                f"'{chosen_name}' for layer '{layer.name}'."
            )

        array = np.asarray(dataset[()])

        expected_shape = tuple(weight_var.shape)
        actual_shape = tuple(array.shape)

        if expected_shape != actual_shape:
            fail(
                f"Shape mismatch for layer '{layer.name}', "
                f"weight base '{base}' "
                f"(matched H5 dataset '{chosen_name}').\n"
                f"Expected: {expected_shape}\n"
                f"Received: {actual_shape}"
            )

        arrays.append(array)

    return arrays


# ============================================================
# MODEL LAYER COLLECTION
# ============================================================

def iter_weighted_leaf_layers(model):
    """
    Recursively walk the model, yielding actual leaf layers that
    own weights directly (Conv2D, Dense, BatchNorm, etc.), not
    container/functional models like the MobileNetV2 base.
    """

    visited = set()

    def walk(layer):

        layer_id = id(layer)

        if layer_id in visited:
            return

        visited.add(layer_id)

        children = []

        if hasattr(layer, "layers"):
            try:
                children = list(layer.layers)
            except Exception:
                children = []

        if children:
            for child in children:
                yield from walk(child)

            direct_weights = getattr(layer, "_trainable_weights", [])
            nontrainable = getattr(layer, "_non_trainable_weights", [])

            if direct_weights or nontrainable:
                child_weight_ids = set()

                for child in children:
                    for weight in getattr(child, "weights", []):
                        child_weight_ids.add(id(weight))

                own_weights = [
                    weight
                    for weight in layer.weights
                    if id(weight) not in child_weight_ids
                ]

                if own_weights:
                    yield layer

            return

        if layer.weights:
            yield layer

    yield from walk(model)


# ============================================================
# STRICT H5 WEIGHT VERIFICATION + LOADING
# ============================================================

def strict_load_h5_weights(model):
    """
    Strictly load and verify every expected weighted layer.

    Weights are matched to H5 datasets BY NAME (kernel/bias/
    gamma/beta/moving_mean/moving_variance), never by raw
    positional zip — that was the source of the earlier
    'dense/bias expected (1280,128) received (128,)' mismatch:
    alphabetical/traversal dataset order does not match
    layer.weights order (e.g. Dense stores [kernel, bias], but
    "bias" sorts before "kernel" alphabetically).

    No silent skipping. No skip_mismatch.
    """

    print()
    print("=" * 72)
    print("[3] STRICT H5 WEIGHT STRUCTURE")
    print("=" * 72)

    if not os.path.isfile(MODEL_PATH):
        fail(
            f"Model file not found:\n{MODEL_PATH}"
        )

    try:
        h5_file = h5py.File(
            MODEL_PATH,
            "r",
        )
    except Exception as exc:
        fail(
            f"Could not open H5 file:\n{exc}"
        )

    try:
        check(
            "model_weights" in h5_file,
            "H5 contains model_weights group.",
        )

        model_weights_group = h5_file["model_weights"]

        h5_groups = collect_h5_groups(
            model_weights_group
        )

        weighted_layers = list(
            iter_weighted_leaf_layers(model)
        )

        check(
            len(weighted_layers) > 0,
            "Rebuilt model contains weighted layers.",
        )

        print(
            f"Weighted leaf layers in rebuilt model: "
            f"{len(weighted_layers)}"
        )

        verified_layers = 0
        verified_tensors = 0

        for layer in weighted_layers:

            result = find_unique_group_for_layer(
                layer,
                h5_groups,
            )

            if result is None:
                fail(
                    "Missing or ambiguous H5 group for "
                    f"layer '{layer.name}'."
                )

            group_path, group = result

            h5_weight_names = get_h5_weight_names(
                group
            )

            expected_weights = list(
                layer.weights
            )

            expected_count = len(
                expected_weights
            )

            actual_count = len(
                h5_weight_names
            )

            if expected_count != actual_count:
                fail(
                    f"Tensor count mismatch for layer "
                    f"'{layer.name}'.\n"
                    f"Model expects: {expected_count}\n"
                    f"H5 contains:   {actual_count}\n"
                    f"H5 group:      {group_path}\n"
                    f"H5 datasets:   {h5_weight_names}"
                )

            if expected_count == 0:
                continue

            # NAME-BASED matching (fixes the positional-order bug).
            arrays = match_weights_by_name(
                layer,
                group,
                h5_weight_names,
            )

            layer.set_weights(arrays)

            verified_layers += 1
            verified_tensors += expected_count

        print()
        print(
            f"Verified weighted layers: {verified_layers}"
        )
        print(
            f"Verified tensors: {verified_tensors}"
        )

        check(
            verified_layers == len(weighted_layers),
            "Every weighted layer was loaded and verified.",
        )

        # ====================================================
        # [4] STRICT SHAPE / COUNT VERIFICATION
        # ====================================================

        print()
        print("=" * 72)
        print("[4] STRICT TENSOR SHAPE + COUNT VERIFICATION")
        print("=" * 72)

        total_model_tensors = sum(
            len(layer.weights)
            for layer in weighted_layers
        )

        check(
            verified_tensors == total_model_tensors,
            "Model tensor count exactly matches verified H5 tensor count.",
        )

        print(
            f"Model tensors expected : {total_model_tensors}"
        )
        print(
            f"H5 tensors verified    : {verified_tensors}"
        )

        # ====================================================
        # [5] STRICT VALUE VERIFICATION
        # ====================================================

        print()
        print("=" * 72)
        print("[5] STRICT WEIGHT VALUE VERIFICATION")
        print("=" * 72)

        value_checked = 0
        value_mismatches = []

        for layer in weighted_layers:

            result = find_unique_group_for_layer(
                layer,
                h5_groups,
            )

            if result is None:
                fail(
                    f"Cannot re-find H5 group for "
                    f"'{layer.name}' during value verification."
                )

            group_path, group = result

            h5_weight_names = get_h5_weight_names(
                group
            )

            live_weights = list(
                layer.get_weights()
            )

            if len(live_weights) != len(
                h5_weight_names
            ):
                fail(
                    f"Value verification tensor count mismatch "
                    f"for '{layer.name}'."
                )

            # Re-match by name (same logic as load), then compare
            # the now-loaded live weight against the H5 source.
            expected_arrays = match_weights_by_name(
                layer,
                group,
                h5_weight_names,
            )

            for index, expected in enumerate(expected_arrays):

                actual = np.asarray(
                    live_weights[index]
                )

                if expected.shape != actual.shape:
                    fail(
                        f"Value verification shape mismatch "
                        f"for '{layer.name}', weight index {index}."
                    )

                if not np.array_equal(
                    expected,
                    actual,
                ):
                    value_mismatches.append(
                        (
                            layer.name,
                            weight_base_name(layer.weights[index]),
                        )
                    )

                value_checked += 1

        print(
            f"Weight tensors value-checked: {value_checked}"
        )

        if value_mismatches:
            print()
            print("First mismatches:")

            for layer_name, weight_name in (
                value_mismatches[:10]
            ):
                print(
                    f"  {layer_name} / {weight_name}"
                )

            fail(
                f"Found {len(value_mismatches)} "
                "weight value mismatches."
            )

        check(
            value_checked == total_model_tensors,
            "Every model tensor was value-checked.",
        )

        check(
            len(value_mismatches) == 0,
            "All loaded H5 weight values exactly match the source tensors.",
        )

        return h5_file

    except SystemExit:
        h5_file.close()
        raise

    except Exception as exc:
        h5_file.close()
        fail(
            "Unexpected strict H5 verification error:\n"
            f"{type(exc).__name__}: {exc}"
        )

    return h5_file


# ============================================================
# CLASS MAP VERIFICATION
# ============================================================

def verify_class_map():
    print()
    print("=" * 72)
    print("[6] CLASS MAP VERIFICATION")
    print("=" * 72)

    if not os.path.isfile(CLASS_MAP_PATH):
        fail(
            f"Class map file not found:\n{CLASS_MAP_PATH}"
        )

    try:
        with open(
            CLASS_MAP_PATH,
            "r",
            encoding="utf-8",
        ) as file:
            class_map = json.load(file)
    except Exception as exc:
        fail(
            f"Could not read class map:\n{exc}"
        )

    expected = {
        "fake": 0,
        "genuine": 1,
    }

    normalized = {
        str(key): int(value)
        for key, value in class_map.items()
    }

    check(
        normalized == expected,
        "Class mapping is exactly fake=0, genuine=1.",
    )

    print(
        f"Class map: {normalized}"
    )


# ============================================================
# SANITY TEST
# ============================================================

def predict_one(model, path):
    array = load_image(path)

    batch = np.expand_dims(
        array,
        axis=0,
    )

    probability = float(
        model.predict(
            batch,
            verbose=0,
        )[0][0]
    )

    if probability >= THRESHOLD:
        verdict = "ORIGINAL"
        confidence = probability
    else:
        verdict = "EDITED"
        confidence = 1.0 - probability

    return probability, verdict, confidence


def sanity_test(model):
    print()
    print("=" * 72)
    print("[7] SANITY TEST")
    print("=" * 72)

    genuine_dir = os.path.join(
        TEST_DIR,
        "genuine",
    )

    fake_dir = os.path.join(
        TEST_DIR,
        "fake",
    )

    genuine_files = list_images(
        genuine_dir
    )

    fake_files = list_images(
        fake_dir
    )

    check(
        len(genuine_files) > 0,
        "Genuine test images found.",
    )

    check(
        len(fake_files) > 0,
        "Fake test images found.",
    )

    genuine_sample = genuine_files[:10]
    fake_sample = fake_files[:10]

    print()
    print("Genuine sanity samples:")

    genuine_probs = []

    for path in genuine_sample:
        probability, verdict, confidence = predict_one(
            model,
            path,
        )

        genuine_probs.append(probability)

        print(
            f"  {os.path.basename(path)}"
            f" -> {verdict}"
            f" | P(genuine)={probability:.6f}"
            f" | confidence={confidence * 100:.2f}%"
        )

    print()
    print("Fake sanity samples:")

    fake_probs = []

    for path in fake_sample:
        probability, verdict, confidence = predict_one(
            model,
            path,
        )

        fake_probs.append(probability)

        print(
            f"  {os.path.basename(path)}"
            f" -> {verdict}"
            f" | P(genuine)={probability:.6f}"
            f" | confidence={confidence * 100:.2f}%"
        )

    all_probs = (
        genuine_probs +
        fake_probs
    )

    check(
        all(
            0.0 <= p <= 1.0
            for p in all_probs
        ),
        "All sanity probabilities are within [0,1].",
    )

    mean_genuine = float(
        np.mean(genuine_probs)
    )

    mean_fake = float(
        np.mean(fake_probs)
    )

    print()
    print(
        f"Mean P(genuine) - genuine samples: "
        f"{mean_genuine:.6f}"
    )

    print(
        f"Mean P(genuine) - fake samples:    "
        f"{mean_fake:.6f}"
    )

    check(
        mean_genuine > mean_fake,
        "Genuine samples have higher mean P(genuine) than fake samples.",
    )


# ============================================================
# FULL TEST
# ============================================================

def full_test(model):
    print()
    print("=" * 72)
    print("[8] FULL TEST SET EVALUATION")
    print("=" * 72)

    records = []

    for class_name in CLASS_NAMES:

        class_dir = os.path.join(
            TEST_DIR,
            class_name,
        )

        expected_label = LABEL_MAP[
            class_name
        ]

        files = list_images(
            class_dir
        )

        print(
            f"{class_name}: {len(files)} images"
        )

        for path in files:

            probability, verdict, confidence = predict_one(
                model,
                path,
            )

            predicted_label = (
                1
                if probability >= THRESHOLD
                else 0
            )

            records.append(
                {
                    "path": path,
                    "class_name": class_name,
                    "y_true": expected_label,
                    "y_pred": predicted_label,
                    "probability_genuine": probability,
                    "verdict": verdict,
                    "confidence": confidence,
                }
            )

    check(
        len(records) == 328,
        f"Full test set contains exactly 328 images "
        f"(found {len(records)}).",
    )

    y_true = [
        record["y_true"]
        for record in records
    ]

    y_pred = [
        record["y_pred"]
        for record in records
    ]

    probabilities = [
        record["probability_genuine"]
        for record in records
    ]

    accuracy = accuracy_score(
        y_true,
        y_pred,
    )

    precision = precision_score(
        y_true,
        y_pred,
        zero_division=0,
    )

    recall = recall_score(
        y_true,
        y_pred,
        zero_division=0,
    )

    f1 = f1_score(
        y_true,
        y_pred,
        zero_division=0,
    )

    try:
        auc = roc_auc_score(
            y_true,
            probabilities,
        )
    except Exception:
        auc = float("nan")

    cm = confusion_matrix(
        y_true,
        y_pred,
        labels=[0, 1],
    )

    print()
    print("-" * 72)
    print("RESULTS")
    print("-" * 72)

    print(
        f"Accuracy  : {accuracy * 100:.2f}%"
    )

    print(
        f"Precision : {precision * 100:.2f}%"
    )

    print(
        f"Recall    : {recall * 100:.2f}%"
    )

    print(
        f"F1        : {f1 * 100:.2f}%"
    )

    if np.isfinite(auc):
        print(
            f"ROC-AUC   : {auc:.4f}"
        )

    print()
    print(
        "Confusion matrix "
        "(rows=true, columns=predicted):"
    )

    print(
        "              predicted"
    )

    print(
        "              EDITED  ORIGINAL"
    )

    print(
        f"true EDITED   {cm[0,0]:6d}  {cm[0,1]:8d}"
    )

    print(
        f"true ORIGINAL  {cm[1,0]:6d}  {cm[1,1]:8d}"
    )

    genuine_records = [
        record
        for record in records
        if record["y_true"] == 1
    ]

    fake_records = [
        record
        for record in records
        if record["y_true"] == 0
    ]

    genuine_probs = [
        record["probability_genuine"]
        for record in genuine_records
    ]

    fake_probs = [
        record["probability_genuine"]
        for record in fake_records
    ]

    print()
    print(
        f"Mean P(genuine) - genuine: "
        f"{np.mean(genuine_probs):.6f}"
    )

    print(
        f"Min  P(genuine) - genuine: "
        f"{np.min(genuine_probs):.6f}"
    )

    print(
        f"Max  P(genuine) - genuine: "
        f"{np.max(genuine_probs):.6f}"
    )

    print(
        f"Mean P(genuine) - fake:    "
        f"{np.mean(fake_probs):.6f}"
    )

    print(
        f"Min  P(genuine) - fake:    "
        f"{np.min(fake_probs):.6f}"
    )

    print(
        f"Max  P(genuine) - fake:    "
        f"{np.max(fake_probs):.6f}"
    )

    wrong = [
        record
        for record in records
        if record["y_true"] != record["y_pred"]
    ]

    print()
    print(
        f"Wrong predictions: {len(wrong)} / {len(records)}"
    )

    if wrong:
        print()
        print("Wrong prediction details:")

        for record in wrong:
            relative = os.path.relpath(
                record["path"],
                TEST_DIR,
            )

            actual = (
                "ORIGINAL"
                if record["y_true"] == 1
                else "EDITED"
            )

            print(
                f"  {relative}"
                f" | actual={actual}"
                f" | predicted={record['verdict']}"
                f" | P(genuine)="
                f"{record['probability_genuine']:.6f}"
            )

    print()
    print("=" * 72)
    print("FINAL VERIFICATION STATUS")
    print("=" * 72)

    print(
        f"Test images : {len(records)}"
    )

    print(
        f"Accuracy    : {accuracy * 100:.2f}%"
    )

    print(
        f"F1 score    : {f1 * 100:.2f}%"
    )

    if np.isfinite(auc):
        print(
            f"ROC-AUC     : {auc:.4f}"
        )

    print(
        f"Errors      : {len(wrong)}"
    )

    print()

    return {
        "accuracy": accuracy,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "auc": auc,
        "wrong": len(wrong),
    }


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("=" * 72)
    print("TRUEPROOF - STRICT MOBILENETV2 VERIFICATION")
    print("=" * 72)

    print()
    print(
        f"TensorFlow: {tf.__version__}"
    )

    print(
        f"Model: {MODEL_PATH}"
    )

    print(
        f"Test dir: {TEST_DIR}"
    )

    print()
    print(
        "Semantics:"
    )

    print(
        "  0 = EDITED / FAKE"
    )

    print(
        "  1 = ORIGINAL / GENUINE"
    )

    print(
        "  output = P(genuine)"
    )

    # ========================================================
    # [1] H5 OPEN
    # ========================================================

    print()
    print("=" * 72)
    print("[1] H5 FILE CHECK")
    print("=" * 72)

    check(
        os.path.isfile(MODEL_PATH),
        f"Model file exists: {MODEL_PATH}",
    )

    try:
        with h5py.File(
            MODEL_PATH,
            "r",
        ) as file:

            root_keys = list(
                file.keys()
            )

            print(
                f"Root keys: {root_keys}"
            )

            check(
                "model_weights" in root_keys,
                "model_weights group exists.",
            )

    except Exception as exc:
        fail(
            f"H5 open/read failed:\n{exc}"
        )

    # ========================================================
    # [2] BUILD EXACT MODEL
    # ========================================================

    print()
    print("=" * 72)
    print("[2] EXACT MODEL REBUILD")
    print("=" * 72)

    try:
        model = build_exact_model()
    except Exception as exc:
        fail(
            "Could not rebuild exact architecture:\n"
            f"{type(exc).__name__}: {exc}"
        )

    check(
        model.input_shape == (None, 224, 224, 3),
        f"Input shape is {model.input_shape}.",
    )

    check(
        model.output_shape == (None, 1),
        f"Output shape is {model.output_shape}.",
    )

    input_name = model.input.name

    print(
        f"Model input tensor: {input_name}"
    )

    print(
        f"Model output tensor: {model.output.name}"
    )

    check(
        any(
            layer.name == "input_image"
            for layer in model.layers
        ),
        "Input layer is named input_image.",
    )

    check(
        any(
            layer.name == "mobilenetv2_1.00_224"
            for layer in model.layers
        ),
        "MobileNetV2 base layer is named mobilenetv2_1.00_224.",
    )

    check(
        any(
            layer.name == "genuine_probability"
            for layer in model.layers
        ),
        "Output layer is named genuine_probability.",
    )

    # ========================================================
    # STRICT H5 LOAD + VERIFICATION
    # ========================================================

    h5_file = strict_load_h5_weights(
        model
    )

    h5_file.close()

    # ========================================================
    # CLASS MAP
    # ========================================================

    verify_class_map()

    # ========================================================
    # SANITY
    # ========================================================

    sanity_test(
        model
    )

    # ========================================================
    # FULL TEST
    # ========================================================

    results = full_test(
        model
    )

    # ========================================================
    # FINAL
    # ========================================================

    print()
    print("=" * 72)
    print("MOBILENETV2 EVALUATION COMPLETE")
    print("=" * 72)

    print(
        f"Accuracy : {results['accuracy'] * 100:.2f}%"
    )

    print(
        f"Precision: {results['precision'] * 100:.2f}%"
    )

    print(
        f"Recall   : {results['recall'] * 100:.2f}%"
    )

    print(
        f"F1       : {results['f1'] * 100:.2f}%"
    )

    if np.isfinite(results["auc"]):
        print(
            f"ROC-AUC  : {results['auc']:.4f}"
        )

    print(
        f"Errors   : {results['wrong']}"
    )

    print()
    print("DONE.")


if __name__ == "__main__":
    main()