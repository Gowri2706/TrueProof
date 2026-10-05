from pathlib import Path
import re

ROOT = Path("dataset")
APPS = ["Gpay", "Phonepe", "Paytm"]
SPLITS = ["train", "val", "test"]

def pair_key(filename):
    m = re.search(r"(\d+)(?=\.[^.]+$)", filename)
    return m.group(1) if m else None

sets = {}

print("===== TRUE PAIR LEAKAGE CHECK =====")

for split in SPLITS:
    sets[split] = set()

    for app in APPS:
        folder = ROOT / split / "genuine" / app

        for p in folder.iterdir():
            if p.is_file():
                key = pair_key(p.name)
                if key:
                    sets[split].add((app, key))

    print(f"{split}: {len(sets[split])} unique pairs")

train_val = sets["train"] & sets["val"]
train_test = sets["train"] & sets["test"]
val_test = sets["val"] & sets["test"]

print()
print("train ∩ val  :", len(train_val))
print("train ∩ test :", len(train_test))
print("val ∩ test   :", len(val_test))

print()

if not train_val and not train_test and not val_test:
    print("PASS: No pair leakage between train/val/test.")
else:
    print("FAIL: Pair leakage detected!")

print("====================================")