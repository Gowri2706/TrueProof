"""
TrueProof - MobileNetV2 Transfer Learning
------------------------------------------
IMPORTANT:
    0 = EDITED / FAKE
    1 = ORIGINAL / GENUINE

Model output:
    P(genuine)

This is a NEW model.
It does NOT overwrite the existing custom CNN model.

FIX (this version):
  1. Phase 1 and Phase 2 now save to SEPARATE checkpoint files
     (trueproof_model_mobilenetv2_phase1.h5 / _phase2.h5), so Phase 2
     can never silently overwrite a better Phase 1 result.
  2. The old `tf.keras.models.load_model(MODEL_OUT)` round-trip at the
     end has been REMOVED — that is what caused
     "Unknown layer: 'TrueDivide'". This model's input includes
     `mobilenet_v2.preprocess_input` applied directly to the Input
     tensor, which embeds a low-level TF op layer that this TF/Keras
     version cannot always reconstruct from a fresh H5 load.
  3. Instead, at the end we compare Phase 1's and Phase 2's best
     val_loss and simply COPY (file copy, not load_model) whichever
     checkpoint file is genuinely better to MODEL_OUT. No re-loading,
     no crash risk, and Phase 2 can never overwrite a better Phase 1.
"""

import os
import json
import shutil
import tensorflow as tf
from tensorflow.keras import layers, models, optimizers, callbacks


# ============================================================
# PATHS
# ============================================================

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "dataset")

TRAIN_DIR = os.path.join(DATASET_DIR, "train")
VAL_DIR = os.path.join(DATASET_DIR, "val")

MODEL_DIR = os.path.dirname(os.path.abspath(__file__))

MODEL_OUT = os.path.join(
    MODEL_DIR,
    "trueproof_model_mobilenetv2.h5"
)

PHASE1_MODEL_OUT = os.path.join(
    MODEL_DIR,
    "trueproof_model_mobilenetv2_phase1.h5"
)

PHASE2_MODEL_OUT = os.path.join(
    MODEL_DIR,
    "trueproof_model_mobilenetv2_phase2.h5"
)

CLASS_MAP_OUT = os.path.join(
    MODEL_DIR,
    "mobilenetv2_class_indices.json"
)

HISTORY_OUT = os.path.join(
    MODEL_DIR,
    "mobilenetv2_training_history.png"
)


# ============================================================
# SETTINGS
# ============================================================

IMG_SIZE = (224, 224)
BATCH_SIZE = 32

EPOCHS_HEAD = 12
EPOCHS_FINETUNE = 8

SEED = 42


# ============================================================
# CLASS SEMANTICS
# ============================================================

# MUST remain (matches the existing custom CNN's convention,
# confirmed earlier via compare_h5_vs_tflite.py):
# 0 = fake / edited
# 1 = genuine / original

CLASS_NAMES = ["fake", "genuine"]

LABEL_MAP = {
    "fake": 0,
    "genuine": 1
}


# ============================================================
# CHECK DATASET
# ============================================================

def list_files(split_dir):

    paths = []
    labels = []

    for cls_name in CLASS_NAMES:

        cls_dir = os.path.join(split_dir, cls_name)

        if not os.path.isdir(cls_dir):
            continue

        label = LABEL_MAP[cls_name]

        for app in sorted(os.listdir(cls_dir)):

            app_dir = os.path.join(cls_dir, app)

            if not os.path.isdir(app_dir):
                continue

            for filename in sorted(os.listdir(app_dir)):

                if filename.lower().endswith(
                    (".png", ".jpg", ".jpeg")
                ):
                    paths.append(
                        os.path.join(app_dir, filename)
                    )
                    labels.append(label)

    return paths, labels


# ============================================================
# LOAD IMAGES
# ============================================================

def make_dataset(paths, labels, shuffle):

    path_ds = tf.data.Dataset.from_tensor_slices(paths)
    label_ds = tf.data.Dataset.from_tensor_slices(labels)

    def load_image(path, label):

        image_bytes = tf.io.read_file(path)

        image = tf.image.decode_image(
            image_bytes,
            channels=3,
            expand_animations=False
        )

        image.set_shape([None, None, 3])

        image = tf.image.resize(
            image,
            IMG_SIZE
        )

        # RAW 0-255 float32
        # MobileNetV2 preprocessing is inside the model.
        image = tf.cast(image, tf.float32)

        label = tf.cast(label, tf.float32)

        return image, label

    dataset = tf.data.Dataset.zip(
        (path_ds, label_ds)
    )

    dataset = dataset.map(
        load_image,
        num_parallel_calls=tf.data.AUTOTUNE
    )

    if shuffle:
        dataset = dataset.shuffle(
            buffer_size=max(len(paths), 1),
            seed=SEED,
            reshuffle_each_iteration=True
        )

    dataset = dataset.batch(BATCH_SIZE)
    dataset = dataset.prefetch(tf.data.AUTOTUNE)

    return dataset


# ============================================================
# BUILD DATASETS
# ============================================================

def build_datasets():

    train_paths, train_labels = list_files(TRAIN_DIR)
    val_paths, val_labels = list_files(VAL_DIR)

    if not train_paths:
        raise SystemExit(
            f"[ERROR] No training images found:\n{TRAIN_DIR}"
        )

    if not val_paths:
        raise SystemExit(
            f"[ERROR] No validation images found:\n{VAL_DIR}"
        )

    train_fake = train_labels.count(0)
    train_genuine = train_labels.count(1)

    val_fake = val_labels.count(0)
    val_genuine = val_labels.count(1)

    print()
    print("============================================================")
    print("DATASET CHECK")
    print("============================================================")

    print(
        f"Train images : {len(train_paths)}"
    )
    print(
        f"  EDITED/FAKE    = {train_fake}"
    )
    print(
        f"  ORIGINAL/GENUINE = {train_genuine}"
    )

    print(
        f"Val images   : {len(val_paths)}"
    )
    print(
        f"  EDITED/FAKE    = {val_fake}"
    )
    print(
        f"  ORIGINAL/GENUINE = {val_genuine}"
    )

    print()
    print("Label mapping:")
    print("  0 = EDITED / FAKE")
    print("  1 = ORIGINAL / GENUINE")
    print("============================================================")
    print()

    return (
        make_dataset(
            train_paths,
            train_labels,
            shuffle=True
        ),
        make_dataset(
            val_paths,
            val_labels,
            shuffle=False
        )
    )


# ============================================================
# BUILD MOBILENETV2 MODEL
# ============================================================

def build_model():

    print("Loading MobileNetV2 ImageNet weights...")

    base = tf.keras.applications.MobileNetV2(
        input_shape=IMG_SIZE + (3,),
        include_top=False,
        weights="imagenet"
    )

    # Phase 1:
    # Freeze pretrained feature extractor.
    base.trainable = False

    # --------------------------------------------------------
    # INPUT
    # --------------------------------------------------------

    inputs = layers.Input(
        shape=IMG_SIZE + (3,),
        name="input_image"
    )

    # --------------------------------------------------------
    # MobileNetV2 preprocessing
    #
    # RAW 0-255
    #       ↓
    # [-1, +1]
    #
    # This means Android can later continue sending
    # RAW 0-255 pixels exactly like the current pipeline.
    # --------------------------------------------------------

    x = tf.keras.applications.mobilenet_v2.preprocess_input(
        inputs
    )

    # --------------------------------------------------------
    # PRETRAINED BACKBONE
    # --------------------------------------------------------

    x = base(
        x,
        training=False
    )

    # --------------------------------------------------------
    # CLASSIFICATION HEAD
    # --------------------------------------------------------

    x = layers.GlobalAveragePooling2D()(x)

    x = layers.Dropout(0.30)(x)

    x = layers.Dense(
        128,
        activation="relu"
    )(x)

    x = layers.Dropout(0.30)(x)

    # IMPORTANT:
    # sigmoid output = P(GENUINE)
    #
    # 0.0 -> EDITED
    # 1.0 -> ORIGINAL
    outputs = layers.Dense(
        1,
        activation="sigmoid",
        name="genuine_probability"
    )(x)

    model = models.Model(
        inputs,
        outputs,
        name="TrueProof_MobileNetV2"
    )

    return model, base


# ============================================================
# CALLBACKS (now takes checkpoint_path so each phase saves separately)
# ============================================================

def make_callbacks(checkpoint_path):

    return [

        callbacks.EarlyStopping(
            monitor="val_loss",
            patience=4,
            restore_best_weights=True,
            verbose=1
        ),

        callbacks.ModelCheckpoint(
            checkpoint_path,
            monitor="val_loss",
            save_best_only=True,
            mode="min",
            verbose=1
        ),

        callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.3,
            patience=2,
            min_lr=1e-7,
            verbose=1
        )
    ]


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("============================================================")
    print("TrueProof MobileNetV2 Training")
    print("============================================================")
    print()
    print("Existing custom CNN will NOT be modified.")
    print("Dataset split will NOT be changed.")
    print("Augmentation: NONE")
    print("Input: RAW 0-255")
    print("Output: P(GENUINE)")
    print()
    print("0 = EDITED / FAKE")
    print("1 = ORIGINAL / GENUINE")
    print()
    print("============================================================")
    print()

    # --------------------------------------------------------
    # DATA
    # --------------------------------------------------------

    train_ds, val_ds = build_datasets()

    # --------------------------------------------------------
    # MODEL
    # --------------------------------------------------------

    model, base = build_model()

    # --------------------------------------------------------
    # PHASE 1
    # --------------------------------------------------------

    print()
    print("============================================================")
    print("PHASE 1")
    print("MobileNetV2 backbone FROZEN")
    print("Training classification head")
    print(f"Checkpoint: {PHASE1_MODEL_OUT}")
    print("============================================================")
    print()

    model.compile(
        optimizer=optimizers.Adam(
            learning_rate=1e-3
        ),
        loss="binary_crossentropy",
        metrics=[
            "accuracy",
            tf.keras.metrics.AUC(
                name="auc"
            ),
            tf.keras.metrics.Precision(
                name="precision"
            ),
            tf.keras.metrics.Recall(
                name="recall"
            )
        ]
    )

    model.summary()

    history1 = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS_HEAD,
        callbacks=make_callbacks(PHASE1_MODEL_OUT)
    )

    # --------------------------------------------------------
    # PHASE 2
    # --------------------------------------------------------

    print()
    print("============================================================")
    print("PHASE 2")
    print("Fine-tuning TOP 30 MobileNetV2 layers")
    print("Learning rate = 1e-5")
    print(f"Checkpoint: {PHASE2_MODEL_OUT}")
    print("============================================================")
    print()

    base.trainable = True

    # Freeze everything except final 30 layers.
    for layer in base.layers[:-30]:
        layer.trainable = False

    # Keep BatchNorm layers frozen.
    # This is safer for a relatively small dataset.
    for layer in base.layers:

        if isinstance(
            layer,
            tf.keras.layers.BatchNormalization
        ):
            layer.trainable = False

    model.compile(
        optimizer=optimizers.Adam(
            learning_rate=1e-5
        ),
        loss="binary_crossentropy",
        metrics=[
            "accuracy",
            tf.keras.metrics.AUC(
                name="auc"
            ),
            tf.keras.metrics.Precision(
                name="precision"
            ),
            tf.keras.metrics.Recall(
                name="recall"
            )
        ]
    )

    history2 = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=EPOCHS_FINETUNE,
        callbacks=make_callbacks(PHASE2_MODEL_OUT)
    )

    # --------------------------------------------------------
    # PICK THE GENUINELY BETTER PHASE — NO load_model() ROUND-TRIP.
    # This is a plain file copy, so it can never hit the
    # "Unknown layer: 'TrueDivide'" crash.
    # --------------------------------------------------------

    phase1_best_val_loss = min(history1.history.get("val_loss", [float("inf")]))
    phase2_best_val_loss = min(history2.history.get("val_loss", [float("inf")]))

    print()
    print("============================================================")
    print("COMPARING PHASE 1 vs PHASE 2")
    print("============================================================")
    print(f"Phase 1 best val_loss : {phase1_best_val_loss:.6f}")
    print(f"Phase 2 best val_loss : {phase2_best_val_loss:.6f}")

    if phase2_best_val_loss <= phase1_best_val_loss and os.path.exists(PHASE2_MODEL_OUT):
        winner_path = PHASE2_MODEL_OUT
        print("Winner: PHASE 2 (fine-tuning improved the model)")
    elif os.path.exists(PHASE1_MODEL_OUT):
        winner_path = PHASE1_MODEL_OUT
        print("Winner: PHASE 1 (fine-tuning did not improve val_loss)")
    else:
        raise SystemExit("[ERROR] Neither phase produced a checkpoint file — check for training errors above.")

    shutil.copy(winner_path, MODEL_OUT)
    print(f"Copied winning checkpoint to: {MODEL_OUT}")
    print("============================================================")

    # --------------------------------------------------------
    # CLASS MAP
    # --------------------------------------------------------

    with open(
        CLASS_MAP_OUT,
        "w",
        encoding="utf-8"
    ) as f:

        json.dump(
            {
                "fake": 0,
                "genuine": 1,
                "output": "probability_of_genuine"
            },
            f,
            indent=2
        )

    print(
        f"Class map : {CLASS_MAP_OUT}"
    )

    # --------------------------------------------------------
    # TRAINING CURVE
    # --------------------------------------------------------

    try:

        import matplotlib.pyplot as plt

        def merge(key):

            return (
                history1.history.get(key, [])
                +
                history2.history.get(key, [])
            )

        accuracy = merge("accuracy")
        val_accuracy = merge("val_accuracy")

        loss = merge("loss")
        val_loss = merge("val_loss")

        plt.figure(figsize=(10, 5))

        plt.plot(
            accuracy,
            label="train_accuracy"
        )

        plt.plot(
            val_accuracy,
            label="val_accuracy"
        )

        plt.axvline(
            x=len(history1.history.get("accuracy", [])),
            color="gray",
            linestyle="--",
            label="fine-tune start"
        )

        plt.xlabel("Epoch")
        plt.ylabel("Accuracy")
        plt.title(
            "TrueProof MobileNetV2 Accuracy"
        )

        plt.legend()
        plt.tight_layout()

        plt.savefig(
            HISTORY_OUT,
            dpi=150
        )

        plt.close()

        print(
            f"Training history : {HISTORY_OUT}"
        )

        # Loss plot separately
        loss_plot = os.path.join(
            MODEL_DIR,
            "mobilenetv2_loss.png"
        )

        plt.figure(figsize=(10, 5))

        plt.plot(
            loss,
            label="train_loss"
        )

        plt.plot(
            val_loss,
            label="val_loss"
        )

        plt.axvline(
            x=len(history1.history.get("loss", [])),
            color="gray",
            linestyle="--"
        )

        plt.xlabel("Epoch")
        plt.ylabel("Loss")
        plt.title(
            "TrueProof MobileNetV2 Loss"
        )

        plt.legend()
        plt.tight_layout()

        plt.savefig(
            loss_plot,
            dpi=150
        )

        plt.close()

        print(
            f"Loss history : {loss_plot}"
        )

    except ImportError:

        print(
            "matplotlib not installed."
        )

    # --------------------------------------------------------
    # FINAL MESSAGE
    # --------------------------------------------------------

    print()
    print("============================================================")
    print("TRAINING COMPLETE")
    print("============================================================")
    print()
    print("DO NOT copy this model to Android yet.")
    print()
    print("Next step:")
    print("1. Evaluate on the UNTOUCHED test split.")
    print("2. Compare against custom CNN 97.26% test accuracy.")
    print("3. Check manual genuine_test.png.")
    print("4. Check manual edited_test.png.")
    print("5. Compare confidence distributions.")
    print()
    print("MobileNetV2 is an experiment - higher confidence is NOT")
    print("guaranteed just because it is pretrained.")
    print()
    print("============================================================")


if __name__ == "__main__":
    main()