"""Train on curated raw [N,100,6] windows. Participant splits precede augmentation.

Input NPZ: X (raw m/s² + rad/s), y (0/1/2), groups (anonymous participant IDs).
No random or placeholder model is packaged into the Android app.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np


def normalize(x):
    return np.asarray(x, dtype=np.float32) / np.array([9.81] * 3 + [np.pi] * 3, dtype=np.float32)


def split_groups(groups, seed=42):
    unique = np.unique(groups)
    if len(unique) < 5:
        raise ValueError("At least 5 participant groups are required for train/validation/test splits")
    shuffled = np.random.default_rng(seed).permutation(unique)
    held_out = max(1, len(unique) // 5)
    test, val, train = shuffled[:held_out], shuffled[held_out:2 * held_out], shuffled[2 * held_out:]
    return [np.flatnonzero(np.isin(groups, subset)) for subset in (train, val, test)]


def augment(x, rng):
    """Identical SO(3) rotation of acceleration/gyro; channel-specific sensor noise."""
    output = x.copy()
    for window in output:
        q, _ = np.linalg.qr(rng.normal(size=(3, 3)))
        q[:, 0] *= np.linalg.det(q)
        window[:, :3] = window[:, :3] @ q
        window[:, 3:] = window[:, 3:] @ q
    return output + rng.normal(0, [0.05] * 3 + [0.01] * 3, output.shape).astype(np.float32)


def build_model(tf, class_count=3):
    layers = tf.keras.layers
    inputs = layers.Input(shape=(100, 6))
    x = inputs
    for filters, kernel in ((32, 5), (64, 5), (128, 3)):
        x = layers.Conv1D(filters, kernel, padding="same", activation="relu")(x)
        x = layers.BatchNormalization()(x)
        if filters != 128:
            x = layers.MaxPooling1D(2)(x)
    x = layers.GlobalAveragePooling1D()(x)
    x = layers.Dense(64, activation="relu")(x)
    x = layers.Dropout(0.3)(x)
    return tf.keras.Model(inputs, layers.Dense(class_count, activation="softmax")(x))


def evaluate(tf, model_bytes, x, y, class_count=3):
    runtime = tf.lite.Interpreter(model_content=model_bytes)
    runtime.allocate_tensors()
    source, target = runtime.get_input_details()[0], runtime.get_output_details()[0]
    if tuple(source["shape"]) != (1, 100, 6) or tuple(target["shape"]) != (1, class_count):
        raise ValueError("Exported tensor shapes do not match Android")
    if source["dtype"] != np.float32 or target["dtype"] != np.float32:
        raise ValueError("Android requires FLOAT32 input and output")
    probabilities = []
    for window in x:
        runtime.set_tensor(source["index"], window[None].astype(np.float32))
        runtime.invoke()
        probabilities.append(runtime.get_tensor(target["index"])[0])
    p = np.asarray(probabilities)
    confusion = np.zeros((class_count, class_count), dtype=int)
    for truth, predicted in zip(y, p.argmax(axis=1)):
        confusion[truth, predicted] += 1
    threat = p[:, 1] >= .80
    if class_count == 3:
        threat |= p[:, 2] >= .75
    return {
        "confusion_true_rows_predicted_columns": confusion.tolist(),
        "recall_by_class": (confusion.diagonal() / np.maximum(confusion.sum(axis=1), 1)).tolist(),
        "threat_gate_recall": float(threat[y != 0].mean()),
        "normal_window_false_positive_rate": float(threat[y == 0].mean()),
    }


def load_training_data(path, loose_drop=False):
    with np.load(path, allow_pickle=False) as data:
        if loose_drop:
            if "task" not in data or data["task"].item() != "loose_phone_drop_v1":
                raise ValueError("Use prepare_loose_drop.py with reviewed actual loose-phone recordings; mounted impacts are excluded")
            if "classes" not in data or data["classes"].tolist() != ["NORMAL", "DROP"]:
                raise ValueError("Loose-drop class order must be NORMAL / DROP")
        raw, y, groups = data["X"].astype(np.float32), data["y"], data["groups"].astype(str)
    classes = [0, 1] if loose_drop else [0, 1, 2]
    if raw.ndim != 3 or raw.shape[1:] != (100, 6) or not np.isfinite(raw).all():
        raise ValueError("X must be finite raw [N,100,6] samples")
    if y.shape != (len(raw),) or groups.shape != y.shape or not np.isin(y, classes).all():
        raise ValueError("Invalid labels or groups")
    if not np.all(np.char.str_len(groups) > 0):
        raise ValueError("Anonymous participant groups cannot be empty")
    return raw, y.astype(np.int64), groups


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--output", type=Path, default=Path("motion-output"))
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--loose-drop", action="store_true", help="Train binary NORMAL / actual loose-phone DROP model")
    args = parser.parse_args()
    raw, y, groups = load_training_data(args.dataset, args.loose_drop)
    class_count = 2 if args.loose_drop else 3
    if args.epochs < 1:
        raise ValueError("epochs must be positive")
    train, val, test = split_groups(groups)
    for name, indices in (("train", train), ("validation", val), ("test", test)):
        if set(y[indices]) != set(range(class_count)):
            raise ValueError(f"{name} split must contain all classes; collect more participants/trials")
    import tensorflow as tf
    tf.keras.utils.set_random_seed(42)
    args.output.mkdir(parents=True, exist_ok=True)
    training_x = normalize(np.concatenate([raw[train], augment(raw[train], np.random.default_rng(42))]))
    training_y = np.tile(y[train], 2)
    model = build_model(tf, class_count)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(training_x, training_y, validation_data=(normalize(raw[val]), y[val]),
              epochs=args.epochs, batch_size=32, class_weight={key: value for key, value in {0: 1., 1: 2.5, 2: 4.}.items() if key < class_count},
              callbacks=[tf.keras.callbacks.EarlyStopping(patience=6, restore_best_weights=True)])
    model.save(args.output / "motion.keras")
    # A concrete batch-1 inference function avoids a dynamic Android input shape.
    @tf.function(input_signature=[tf.TensorSpec([1, 100, 6], tf.float32)])
    def serving(x):
        return model(x, training=False)
    converter = tf.lite.TFLiteConverter.from_concrete_functions([serving.get_concrete_function()], model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    calibration = normalize(raw[train[:min(300, len(train))]])
    converter.representative_dataset = lambda: ([window[None]] for window in calibration)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32
    converter.inference_output_type = tf.float32
    exported = converter.convert()
    report = evaluate(tf, exported, normalize(raw[test]), y[test], class_count)
    report["classes"] = ["NORMAL", "DROP"] if args.loose_drop else ["NORMAL", "DROP", "SNATCH"]
    report["scope"] = "Held-out participant windows only; not continuous monitoring or unseen-phone validation"
    report["participant_splits"] = {name: np.unique(groups[indices]).tolist() for name, indices in (("train", train), ("validation", val), ("test", test))}
    asset = "loose_phone_drop_classifier.tflite" if args.loose_drop else "motion_threat_classifier.tflite"
    (args.output / asset).write_bytes(exported)
    if args.loose_drop:
        manifest = dict(task="loose_phone_drop_v1", classes=["NORMAL", "DROP"],
                        model_sha256=hashlib.sha256(exported).hexdigest(),
                        dataset_sha256=hashlib.sha256(args.dataset.read_bytes()).hexdigest(),
                        input_shape=[1, 100, 6], sample_rate_hz=50,
                        acceleration="raw_including_gravity_m_s2", gyroscope="rad_s",
                        threshold=0.80, evaluation=report)
        (args.output / "loose_phone_drop_classifier.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (args.output / "evaluation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
