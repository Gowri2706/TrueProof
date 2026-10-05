"""
archive_old_dataset.py
--------------------------
Moves your CURRENT dataset/ folder (the one that had the account-reuse
problem) into a timestamped backup folder, WITHOUT deleting anything.

Run this ONCE, before creating the new genuine/fake pair-based folders.

Usage (from python_ai/ folder):
    python model/archive_old_dataset.py
"""

import os
import shutil
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "dataset")


def main():
    if not os.path.exists(DATASET_DIR):
        print(f"No dataset/ folder found at {DATASET_DIR} — nothing to archive.")
        return

    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_dir = os.path.join(BASE_DIR, f"dataset_old_backup_{stamp}")

    shutil.move(DATASET_DIR, backup_dir)
    print(f"Old dataset moved to: {backup_dir}")
    print("Nothing was deleted — your old data is safe there.")

    os.makedirs(os.path.join(DATASET_DIR, "genuine", "Gpay"), exist_ok=True)
    os.makedirs(os.path.join(DATASET_DIR, "genuine", "Phonepe"), exist_ok=True)
    os.makedirs(os.path.join(DATASET_DIR, "genuine", "Paytm"), exist_ok=True)
    os.makedirs(os.path.join(DATASET_DIR, "fake", "Gpay"), exist_ok=True)
    os.makedirs(os.path.join(DATASET_DIR, "fake", "Phonepe"), exist_ok=True)
    os.makedirs(os.path.join(DATASET_DIR, "fake", "Paytm"), exist_ok=True)

    print(f"\nFresh empty structure created at: {DATASET_DIR}")
    print("""
dataset/
├── genuine/
│   ├── Gpay/       <- put original screenshots here
│   ├── Phonepe/
│   └── Paytm/
└── fake/
    ├── Gpay/       <- put the EDITED version of the SAME screenshot here
    ├── Phonepe/        (same filename/id as its genuine pair)
    └── Paytm/
""")
    print("Next: copy your paired screenshots in, then run model/audit_pairs.py")


if __name__ == "__main__":
    main()
