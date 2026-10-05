"""
pair_split.py
----------------
STEP 2 (after audit_pairs.py passes clean). Splits your paired dataset
into train/val/test at the PAIR level — a genuine screenshot and its
matching fake edit ALWAYS land in the same split. This prevents the
leakage problem flagged earlier (same source image appearing in both
train and validation).

Also stratifies per app (Gpay/Phonepe/Paytm) so each split has a
proportional mix of all three apps, not accidentally all-GPay-in-train.

Output layout:
    dataset/train/genuine/<App>/...
    dataset/train/fake/<App>/...
    dataset/val/genuine/<App>/...
    dataset/val/fake/<App>/...
    dataset/test/genuine/<App>/...
    dataset/test/fake/<App>/...

Run (from python_ai/ folder):
    python model/pair_split.py
"""

import os
import random
import re
import shutil
from collections import defaultdict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "dataset")
APPS = ["Gpay", "Phonepe", "Paytm"]
EXTS = (".png", ".jpg", ".jpeg")

RANDOM_SEED = 42
TRAIN_FRAC = 0.70
VAL_FRAC = 0.15
TEST_FRAC = 0.15  # remainder


def pair_key(filename: str) -> str:
    """
    Extracts the pair id from a filename regardless of naming style:
        "genuine Gpay 1.png"  -> "1"
        "fake Gpay 1.png"     -> "1"
        "001_original.png"    -> "1"
        "001_edit.png"        -> "1"
        "A001.png"            -> "a001"  (no trailing digits -> falls back
                                           to the cleaned stem itself)

    Real-world filenames from phones often look like
    "genuine  Gpay  1.png" (extra spaces, class+app name baked into the
    filename) rather than a clean "1.png" / "001_edit.png". This function
    pulls out the TRAILING NUMBER in the filename (right before the
    extension) as the pair id, which is robust to whatever words/spacing
    come before it.
    """
    import os as _os
    stem = re.sub(r"(_original|_edit|_orig|_fake)$",
                   "", _os.path.splitext(filename)[0],
                   flags=re.IGNORECASE)
    m = re.search(r"(\d+)\s*$", stem)
    if m:
        return str(int(m.group(1)))  # normalizes "001" and "1" to the same key
    return stem.strip().lower()


def scan(folder):
    out = defaultdict(list)
    if not os.path.isdir(folder):
        return out
    for f in os.listdir(folder):
        if f.lower().endswith(EXTS):
            out[pair_key(f)].append(f)
    return out


def main():
    random.seed(RANDOM_SEED)
    summary = defaultdict(lambda: defaultdict(int))

    for app in APPS:
        g_folder = os.path.join(DATASET_DIR, "genuine", app)
        f_folder = os.path.join(DATASET_DIR, "fake", app)

        g_map = scan(g_folder)
        f_map = scan(f_folder)
        matched_keys = sorted(set(g_map.keys()) & set(f_map.keys()))

        if not matched_keys:
            print(f"[SKIP] No matched pairs for {app} — run audit_pairs.py first.")
            continue

        random.shuffle(matched_keys)
        n = len(matched_keys)
        n_train = int(n * TRAIN_FRAC)
        n_val = int(n * VAL_FRAC)

        splits = {
            "train": matched_keys[:n_train],
            "val": matched_keys[n_train:n_train + n_val],
            "test": matched_keys[n_train + n_val:],
        }

        for split_name, keys in splits.items():
            g_out = os.path.join(DATASET_DIR, split_name, "genuine", app)
            f_out = os.path.join(DATASET_DIR, split_name, "fake", app)
            os.makedirs(g_out, exist_ok=True)
            os.makedirs(f_out, exist_ok=True)

            for k in keys:
                # a pair_key should map to exactly one file each side after audit_pairs.py
                g_file = g_map[k][0]
                f_file = f_map[k][0]
                shutil.copy2(os.path.join(g_folder, g_file), os.path.join(g_out, g_file))
                shutil.copy2(os.path.join(f_folder, f_file), os.path.join(f_out, f_file))
                summary[split_name][app] += 1

        print(f"{app}: {n} pairs -> train={len(splits['train'])} "
              f"val={len(splits['val'])} test={len(splits['test'])}")

    print("\n===== FINAL SPLIT SUMMARY (pairs per app) =====")
    for split_name in ["train", "val", "test"]:
        counts = summary[split_name]
        total = sum(counts.values())
        detail = ", ".join(f"{app}={counts.get(app, 0)}" for app in APPS)
        print(f"{split_name:6s}: {total:4d} pairs  ({detail})  "
              f"= {total*2} images (genuine+fake)")

    print("\nNext: python model/train.py")


if __name__ == "__main__":
    main()
