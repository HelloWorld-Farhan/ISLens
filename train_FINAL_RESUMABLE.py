import tensorflow as tf
from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.layers import (
    Dense,
    GlobalAveragePooling2D,
    Dropout,
    BatchNormalization,
)
from tensorflow.keras.models import Model, load_model
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import (
    ModelCheckpoint,
    EarlyStopping,
    ReduceLROnPlateau,
    Callback,
)
from tensorflow.keras import regularizers
import matplotlib.pyplot as plt
import numpy as np
import os
import json

print("=" * 80)
print("🎯 ISL RESUMABLE TRAINING - POWER-LOSS SAFE")
print("=" * 80 + "\n")

# ===================== CONFIG =====================
IMG_SIZE = 224
BATCH_SIZE = 32
PHASE1_EPOCHS = 60
PHASE2_EPOCHS = 30
DATASET = "TheFinalDataSet"

# Checkpoint files
CHECKPOINT_JSON = "training_state.json"
MODEL_P1 = "isl_phase1_latest.h5"
MODEL_P2 = "isl_phase2_latest.h5"
BEST_MODEL = "isl_best.h5"


# ===================== CHECKPOINT SYSTEM =====================
class CheckpointManager(Callback):
    """Save training state after each epoch"""

    def __init__(self, checkpoint_file, phase):
        super().__init__()
        self.checkpoint_file = checkpoint_file
        self.phase = phase

    def on_epoch_end(self, epoch, logs=None):
        state = load_state()
        state["phase"] = self.phase
        state["epoch"] = epoch + 1
        state[f"phase{self.phase}_loss"] = float(logs.get("val_loss", 0))
        state[f"phase{self.phase}_acc"] = float(logs.get("val_accuracy", 0))
        save_state(state)
        print(f"💾 Checkpoint saved: Phase {self.phase}, Epoch {epoch + 1}")


def load_state():
    if os.path.exists(CHECKPOINT_JSON):
        with open(CHECKPOINT_JSON, "r") as f:
            return json.load(f)
    return {"phase": 1, "epoch": 0, "phase1_complete": False, "phase2_complete": False}


def save_state(state):
    with open(CHECKPOINT_JSON, "w") as f:
        json.dump(state, f, indent=2)


state = load_state()
if state["epoch"] > 0:
    print(f"🔄 RESUMING: Phase {state['phase']}, Epoch {state['epoch']}")
    print(f"   Your laptop can turn off anytime - training will resume!\n")
else:
    print("🆕 Starting fresh training\n")


# ===================== DATASET ANALYSIS =====================
print("=" * 80)
print("📊 Analyzing TheFinalDataSet")
print("=" * 80 + "\n")

if not os.path.exists(DATASET):
    print(f"❌ ERROR: {DATASET}/ not found!")
    print("Please create TheFinalDataSet with all your images\n")
    exit(1)

class_counts = {}
for letter in sorted(os.listdir(DATASET)):
    path = os.path.join(DATASET, letter)
    if os.path.isdir(path):
        count = len(
            [
                f
                for f in os.listdir(path)
                if f.lower().endswith((".jpg", ".jpeg", ".png"))
            ]
        )
        class_counts[letter] = count
        print(f"   {letter}: {count:,} images")

total = sum(class_counts.values())
print(f"\n✅ Total: {total:,} images ({total // 26:,} avg per letter)")
print(f"✅ Classes: {len(class_counts)}\n")


# ===================== DATA GENERATORS =====================
print("🔧 Setting up data generators (optimized for all lighting)...\n")

train_gen = ImageDataGenerator(
    rescale=1.0 / 255,
    validation_split=0.15,  # 15% validation
    # Geometric (moderate to preserve hand shape)
    rotation_range=20,
    width_shift_range=0.12,
    height_shift_range=0.12,
    shear_range=0.08,
    zoom_range=[0.88, 1.12],
    horizontal_flip=True,
    # CRITICAL: Lighting augmentation (handles dark/bright/flash)
    brightness_range=[0.6, 1.4],  # Wide range for lighting
    fill_mode="nearest",
)

val_gen = ImageDataGenerator(rescale=1.0 / 255, validation_split=0.15)

train_data = train_gen.flow_from_directory(
    DATASET,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="training",
    shuffle=True,
    seed=42,
)

val_data = val_gen.flow_from_directory(
    DATASET,
    target_size=(IMG_SIZE, IMG_SIZE),
    batch_size=BATCH_SIZE,
    class_mode="categorical",
    subset="validation",
    shuffle=False,
    seed=42,
)

NUM_CLASSES = train_data.num_classes
print(
    f"✅ Training: {train_data.samples:,} | Validation: {val_data.samples:,} | Classes: {NUM_CLASSES}\n"
)


# ===================== BUILD/LOAD MODEL =====================
print("=" * 80)
print("🏗️  Model Setup")
print("=" * 80 + "\n")


def create_model():
    base = MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3),
        include_top=False,
        weights="imagenet",
        alpha=1.0,
    )

    # Freeze 70% (good balance)
    for layer in base.layers[: int(len(base.layers) * 0.70)]:
        layer.trainable = False

    x = base.output
    x = GlobalAveragePooling2D()(x)
    x = Dense(512, activation="relu", kernel_regularizer=regularizers.l2(0.01))(x)
    x = BatchNormalization()(x)
    x = Dropout(0.5)(x)
    x = Dense(256, activation="relu", kernel_regularizer=regularizers.l2(0.01))(x)
    x = BatchNormalization()(x)
    x = Dropout(0.45)(x)
    output = Dense(
        NUM_CLASSES, activation="softmax", kernel_regularizer=regularizers.l2(0.005)
    )(x)

    return Model(inputs=base.input, outputs=output), base


# Check if resuming
if state["phase"] == 1 and state["epoch"] > 0 and os.path.exists(MODEL_P1):
    print(f"📂 Loading Phase 1 checkpoint from epoch {state['epoch']}...")
    model = load_model(MODEL_P1)
    base_model = model.layers[1]  # MobileNetV2 base
    initial_epoch_p1 = state["epoch"]
    print(f"✅ Resumed Phase 1 at epoch {initial_epoch_p1}\n")
elif state["phase"] == 2 and os.path.exists(MODEL_P2):
    print(f"📂 Loading Phase 2 checkpoint from epoch {state['epoch']}...")
    model = load_model(MODEL_P2)
    base_model = model.layers[1]
    initial_epoch_p2 = state["epoch"]
    print(f"✅ Resumed Phase 2 at epoch {initial_epoch_p2}\n")
    state["phase1_complete"] = True
else:
    print("Creating new model...")
    model, base_model = create_model()
    initial_epoch_p1 = 0
    initial_epoch_p2 = 0

print(f"✅ Model: {model.count_params():,} parameters\n")


# ===================== PHASE 1: TRAIN HEAD =====================
if not state.get("phase1_complete", False):
    print("=" * 80)
    print("🚀 PHASE 1: Training (Head Only)")
    print("=" * 80 + "\n")

    model.compile(
        optimizer=Adam(0.0005), loss="categorical_crossentropy", metrics=["accuracy"]
    )

    callbacks_p1 = [
        CheckpointManager(CHECKPOINT_JSON, phase=1),
        ModelCheckpoint(MODEL_P1, save_best_only=False, verbose=0),  # Save every epoch
        ModelCheckpoint(
            BEST_MODEL,
            monitor="val_accuracy",
            save_best_only=True,
            mode="max",
            verbose=1,
        ),
        EarlyStopping(
            monitor="val_loss", patience=10, restore_best_weights=True, verbose=1
        ),
        ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=4, min_lr=1e-7, verbose=1
        ),
    ]

    hist_p1 = model.fit(
        train_data,
        validation_data=val_data,
        epochs=PHASE1_EPOCHS,
        initial_epoch=initial_epoch_p1,
        callbacks=callbacks_p1,
        verbose=1,
    )

    state["phase1_complete"] = True
    state["phase"] = 2
    state["epoch"] = 0
    save_state(state)
    print("\n✅ Phase 1 complete!\n")
else:
    print("✅ Phase 1 already complete, skipping...\n")
    hist_p1 = None


# ===================== PHASE 2: FINE-TUNE =====================
if not state.get("phase2_complete", False):
    print("=" * 80)
    print("🎯 PHASE 2: Fine-Tuning")
    print("=" * 80 + "\n")

    # Unfreeze top 30%
    for layer in base_model.layers[int(len(base_model.layers) * 0.70) :]:
        layer.trainable = True

    model.compile(
        optimizer=Adam(0.00005), loss="categorical_crossentropy", metrics=["accuracy"]
    )

    callbacks_p2 = [
        CheckpointManager(CHECKPOINT_JSON, phase=2),
        ModelCheckpoint(MODEL_P2, save_best_only=False, verbose=0),
        ModelCheckpoint(
            BEST_MODEL,
            monitor="val_accuracy",
            save_best_only=True,
            mode="max",
            verbose=1,
        ),
        EarlyStopping(
            monitor="val_loss", patience=8, restore_best_weights=True, verbose=1
        ),
        ReduceLROnPlateau(
            monitor="val_loss", factor=0.5, patience=3, min_lr=1e-8, verbose=1
        ),
    ]

    hist_p2 = model.fit(
        train_data,
        validation_data=val_data,
        epochs=PHASE2_EPOCHS,
        initial_epoch=initial_epoch_p2,
        callbacks=callbacks_p2,
        verbose=1,
    )

    state["phase2_complete"] = True
    save_state(state)
    print("\n✅ Phase 2 complete!\n")
else:
    print("✅ Phase 2 already complete!\n")
    hist_p2 = None


# ===================== FINAL MODEL =====================
print("Loading best model...")
model = load_model(BEST_MODEL)
model.save("isl_final.h5")


# ===================== EVALUATION =====================
print("\n" + "=" * 80)
print("📊 EVALUATION")
print("=" * 80 + "\n")

val_loss, val_acc = model.evaluate(val_data, verbose=0)
print(f"🎯 Validation Accuracy: {val_acc * 100:.2f}%")
print(f"📉 Validation Loss: {val_loss:.4f}\n")

# Per-letter accuracy
val_data.reset()
preds = model.predict(val_data, verbose=0)
y_pred = np.argmax(preds, axis=1)
y_true = val_data.classes
labels = list(train_data.class_indices.keys())

correct = {l: 0 for l in labels}
total = {l: 0 for l in labels}

for true_idx, pred_idx in zip(y_true, y_pred):
    label = labels[true_idx]
    total[label] += 1
    if true_idx == pred_idx:
        correct[label] += 1

print("PER-LETTER ACCURACY:")
print("-" * 60)

excellent, good, needs_work = 0, 0, 0
for letter in sorted(labels):
    if total[letter] > 0:
        acc = (correct[letter] / total[letter]) * 100
        if acc >= 90:
            status, excellent = "✅", excellent + 1
        elif acc >= 80:
            status, good = "⚠️", good + 1
        else:
            status, needs_work = "❌", needs_work + 1
        print(f"{status} {letter}: {acc:.1f}% ({correct[letter]}/{total[letter]})")

print(f"\n{'=' * 60}")
print(f"✅ Excellent (≥90%): {excellent}/{NUM_CLASSES}")
print(f"⚠️  Good (80-89%):   {good}/{NUM_CLASSES}")
print(f"❌ Needs work (<80%): {needs_work}/{NUM_CLASSES}\n")


# ===================== CONVERT TFLITE =====================
print("📱 Converting to TFLite...\n")

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]


def rep_data():
    for _ in range(100):
        yield [next(train_data)[0].astype(np.float32)]


converter.representative_dataset = rep_data
tflite_model = converter.convert()

with open("isl_ultimate.tflite", "wb") as f:
    f.write(tflite_model)

size_mb = len(tflite_model) / 1024 / 1024
print(f"✅ TFLite: isl_ultimate.tflite ({size_mb:.2f} MB)\n")


# ===================== FINAL REPORT =====================
print("=" * 80)
print("🎉 TRAINING COMPLETE!")
print("=" * 80 + "\n")

print("📁 Files created:")
print("   ✅ isl_ultimate.tflite ← USE THIS IN APP")
print("   ✅ isl_final.h5")
print("   ✅ isl_best.h5\n")

print("📊 Final Results:")
print(f"   Validation Accuracy: {val_acc * 100:.2f}%")
print(f"   Model Size: {size_mb:.2f} MB")
print(f"   Letters ≥90%: {excellent}/{NUM_CLASSES}\n")

if val_acc >= 0.90:
    print("🏆 OUTSTANDING! 90%+ accuracy achieved!")
    print("✅ Model is PRODUCTION READY for your Android app!\n")
elif val_acc >= 0.85:
    print("✅ VERY GOOD! 85%+ accuracy achieved!")
    print("   Ready for deployment\n")
else:
    print("⚠️  Model needs more training")
    print("   Recommendation: Run for more epochs\n")

print("💡 Checkpoint system active:")
print("   - Training state saved after each epoch")
print("   - Safe to stop/resume anytime")
print("   - Survives power loss/shutdowns\n")
print("🚀 Next step: Copy isl_ultimate.tflite to app/src/main/assets/\n")
print("=" * 80 + "\n")
