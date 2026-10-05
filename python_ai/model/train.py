"""
train.py
--------
FINAL step of the PAIR-BASED pipeline — run this LAST, after every step
below has passed clean. This project no longer uses augment_fake.py or
add_whatsapp_style_genuine.py at all — those belonged to the OLD
(single-image, augmentation-based) design and are retired.

Trains a CNN to classify a payment screenshot as GENUINE or FAKE.

CURRENT PIPELINE (run in this exact order, from python_ai/ folder):
    1. python model/archive_old_dataset.py  <- one-time: backs up the old
                                                dataset/, creates a fresh
                                                genuine/<App> + fake/<App>
                                                folder structure
    2. (manual) copy in your paired screenshots:
                dataset/genuine/<App>/<id>.png   <- original
                dataset/fake/<App>/<id>.png      <- the SAME screenshot, edited
    3. python model/audit_pairs.py          <- filename-level pairing check:
                                                every genuine has exactly one
                                                fake, no unpaired/duplicate ids
    4. python model/visual_pair_audit.py    <- PIXEL-level check: confirms each
                                                fake is a real, localised edit
                                                of its genuine pair (not an
                                                exact duplicate, not an
                                                unrelated screenshot)
    5. python model/pair_split.py           <- splits at the PAIR level into
                                                train/val/test (70/15/15,
                                                balanced per app) — a pair's
                                                genuine+fake always land in
                                                the same split
    6. python model/train.py                <- this file
    7. python model/evaluate_test.py        <- final, untouched test accuracy

DATASET LAYOUT THIS SCRIPT EXPECTS (created automatically by step 5 above):
    python_ai/dataset/train/genuine/<App>/...
    python_ai/dataset/train/fake/<App>/...
    python_ai/dataset/val/genuine/<App>/...
    python_ai/dataset/val/fake/<App>/...

WHY THERE IS NO AUGMENTATION ANYWHERE IN THIS DESIGN:
    - NO in-model data_augmentation layer. Genuine images are real,
      untouched screenshots and must stay that way.
    - NO offline augmentation script for fake images either. Every fake
      image is a real, manually/programmatically edited counterpart of
      one specific genuine screenshot (see visual_pair_audit.py) — the
      dataset's realism comes from having many distinct real pairs
      (target: 800-1000+), not from multiplying a few pairs synthetically.
    - class_weight="balanced" is kept as a safety net in case genuine/fake
      counts aren't perfectly equal after the split.

WHAT THIS SCRIPT DOES:
    1. Loads images from dataset/train/{genuine,fake} and dataset/val/{genuine,fake}
    2. Trains a small CNN from scratch (fast, works without GPU)
    3. Saves the best model to model/trueproof_model.h5
    4. Saves a training accuracy/loss graph to model/training_history.png
    5. Prints a full classification report (precision/recall/F1) + confusion
       matrix on the validation set — put THIS in your report, not just
       "accuracy", since accuracy alone can be misleading.
"""

import os
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.preprocessing.image import ImageDataGenerator
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.utils.class_weight import compute_class_weight

IMG_SIZE = (224, 224)
BATCH_SIZE = 16
EPOCHS = 25

DATASET_DIR = os.path.join(os.path.dirname(__file__), "..", "dataset")
TRAIN_DIR = os.path.join(DATASET_DIR, "train")
VAL_DIR = os.path.join(DATASET_DIR, "val")
MODEL_OUT = os.path.join(os.path.dirname(__file__), "trueproof_model.h5")
HISTORY_PLOT = os.path.join(os.path.dirname(__file__), "training_history.png")


def build_model():
    # NOTE: no data_augmentation layer here on purpose — see module docstring.
    model = models.Sequential([
        layers.Input(shape=(*IMG_SIZE, 3)),
        layers.Rescaling(1. / 255),

        layers.Conv2D(32, 3, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling2D(),

        layers.Conv2D(64, 3, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling2D(),

        layers.Conv2D(128, 3, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling2D(),

        layers.Conv2D(128, 3, activation='relu', padding='same'),
        layers.BatchNormalization(),
        layers.MaxPooling2D(),

        layers.GlobalAveragePooling2D(),
        layers.Dense(128, activation='relu'),
        layers.Dropout(0.4),
        layers.Dense(1, activation='sigmoid')   # 0 = fake, 1 = genuine
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-4),
        loss='binary_crossentropy',
        metrics=['accuracy', tf.keras.metrics.Precision(name='precision'),
                 tf.keras.metrics.Recall(name='recall')]
    )
    return model


def count_images(folder):
    total = 0
    for root, dirs, files in os.walk(folder):
        total += len([f for f in files if f.lower().endswith((".png", ".jpg", ".jpeg"))])
    return total


def check_dataset_ready():
    required = [
        os.path.join(TRAIN_DIR, "genuine"),
        os.path.join(TRAIN_DIR, "fake"),
        os.path.join(VAL_DIR, "genuine"),
        os.path.join(VAL_DIR, "fake"),
    ]
    missing = [p for p in required if not os.path.isdir(p) or count_images(p) == 0]
    if missing:
        print("ERROR: expected folders are missing or empty:")
        for m in missing:
            print(f"   - {m}")
        print("\nRun these first, in order:")
        print("   python model/prepare_split.py")
        print("   python model/augment_fake.py")
        raise SystemExit(1)


def main():
    check_dataset_ready()

    train_g = count_images(os.path.join(TRAIN_DIR, "genuine"))
    train_f = count_images(os.path.join(TRAIN_DIR, "fake"))
    val_g = count_images(os.path.join(VAL_DIR, "genuine"))
    val_f = count_images(os.path.join(VAL_DIR, "fake"))

    print("\n========== DATASET INFO ==========")
    print(f"train/genuine : {train_g}")
    print(f"train/fake    : {train_f}")
    print(f"val/genuine   : {val_g}")
    print(f"val/fake      : {val_f}")
    print("===================================\n")

    # No on-the-fly augmentation here — genuine must never be augmented,
    # and fake was already augmented offline by augment_fake.py.
    train_datagen = ImageDataGenerator()
    val_datagen = ImageDataGenerator()

    train_gen = train_datagen.flow_from_directory(
        TRAIN_DIR,
        classes=["fake", "genuine"],   # index 0=fake, 1=genuine — must match model output
        target_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        class_mode='binary',
        shuffle=True,
        seed=42,
    )

    val_gen = val_datagen.flow_from_directory(
        VAL_DIR,
        classes=["fake", "genuine"],
        target_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        class_mode='binary',
        shuffle=False,
        seed=42,
    )

    class_weights = compute_class_weight(
        class_weight="balanced",
        classes=np.unique(train_gen.classes),
        y=train_gen.classes
    )
    class_weights = dict(enumerate(class_weights))

    print("Class Weights:", class_weights)
    print("Class Indices:", train_gen.class_indices)
    print("Training Samples:", train_gen.samples)
    print("Validation Samples:", val_gen.samples)
    print("Training labels:", np.bincount(train_gen.classes.astype(int)))
    print("Validation labels:", np.bincount(val_gen.classes.astype(int)))

    model = build_model()
    model.summary()

    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(MODEL_OUT, save_best_only=True, monitor='val_accuracy', mode='max'),
        tf.keras.callbacks.EarlyStopping(monitor='val_loss', patience=6, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(monitor='val_loss', factor=0.5, patience=3),
    ]

    history = model.fit(
        train_gen,
        validation_data=val_gen,
        epochs=EPOCHS,
        callbacks=callbacks,
        class_weight=class_weights,
    )

    # ---- plots ----
    fig, ax = plt.subplots(1, 2, figsize=(11, 4))
    ax[0].plot(history.history['accuracy'], label='train')
    ax[0].plot(history.history['val_accuracy'], label='val')
    ax[0].set_title('Accuracy'); ax[0].legend()
    ax[1].plot(history.history['loss'], label='train')
    ax[1].plot(history.history['val_loss'], label='val')
    ax[1].set_title('Loss'); ax[1].legend()
    plt.tight_layout()
    plt.savefig(HISTORY_PLOT)
    print(f"Saved training curves to {HISTORY_PLOT}")

    # ---- real evaluation report (put this in your project report) ----
    val_gen.reset()
    y_true = val_gen.classes
    y_pred_prob = model.predict(val_gen)
    y_pred = (y_pred_prob > 0.5).astype(int).flatten()

    print("\n=== Classification Report (validation set) ===")
    print(classification_report(y_true, y_pred, target_names=["fake", "genuine"]))
    print("Confusion matrix:")
    print(confusion_matrix(y_true, y_pred))

    print(f"\nModel saved to: {MODEL_OUT}")
    print("NOTE: val/fake/ contains only real, un-augmented fake screenshots — so this "
          "validation accuracy is an honest estimate, not inflated by augmentation leakage.")
    print("Real-world accuracy on screenshots the model has never seen may still be a bit "
          "lower — mention this honestly in your report/viva.")


if __name__ == "__main__":
    main()
