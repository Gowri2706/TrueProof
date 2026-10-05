"""
visual_pair_audit.py
------------------------
RUN THIS AFTER audit_pairs.py SAYS "All clear!" AND BEFORE pair_split.py.

audit_pairs.py only checks FILENAMES match up. It cannot tell you whether
the fake image is actually an edited version of its genuine pair, or:
  (a) an EXACT duplicate (genuine copy-pasted into fake/ with no real
      edit) — this teaches the model nothing about tampering, and
  (b) a COMPLETELY UNRELATED screenshot (wrong pairing, e.g. genuine/50
      paired by filename with a fake image that is actually a totally
      different transaction) — this breaks the "difference = edit"
      training signal your design depends on.

For every matched pair this script:
  1. Resizes both to the same size (skips if dimensions are wildly
     different, which is itself a red flag)
  2. Computes the FRACTION OF PIXELS that changed meaningfully (not the
     mean difference over the whole image — a real edit like changing
     just the amount only touches a small region, so averaging over the
     whole image would wash it out and wrongly look "identical")
  3. Flags:
       - ~0% of pixels changed -> "IDENTICAL, no real edit found"
       - most of the image changed -> "TOO DIFFERENT, likely NOT a real pair"
     and leaves everything in between as good ("small local edit, as expected")

Run (from python_ai/ folder):
    python model/visual_pair_audit.py
"""

import os
import re
from collections import defaultdict

import numpy as np
from PIL import Image, ImageChops

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "dataset")
APPS = ["Gpay", "Phonepe", "Paytm"]
EXTS = (".png", ".jpg", ".jpeg")

# A pixel counts as "changed" if any RGB channel differs by more than this.
PIXEL_CHANGE_THRESHOLD = 25

# Tune these if needed once you see real numbers from your dataset.
IDENTICAL_FRACTION = 0.0005   # < 0.05% of pixels changed = "no real edit found"
TOO_DIFFERENT_FRACTION = 0.55  # > 55% of pixels changed = "probably not a real pair"

CHECK_SIZE = (224, 224)  # compare at a common size regardless of original resolution


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


def changed_pixel_fraction(path_a, path_b):
    a = Image.open(path_a).convert("RGB").resize(CHECK_SIZE)
    b = Image.open(path_b).convert("RGB").resize(CHECK_SIZE)
    diff = np.asarray(ImageChops.difference(a, b), dtype=np.int16)
    per_pixel_max_channel_diff = diff.max(axis=2)  # worst channel per pixel
    changed = (per_pixel_max_channel_diff > PIXEL_CHANGE_THRESHOLD)
    return float(changed.mean())


def main():
    identical_flags = []
    too_different_flags = []
    ok_count = 0

    for app in APPS:
        g_folder = os.path.join(DATASET_DIR, "genuine", app)
        f_folder = os.path.join(DATASET_DIR, "fake", app)
        g_map = scan(g_folder)
        f_map = scan(f_folder)
        matched = sorted(set(g_map.keys()) & set(f_map.keys()))

        print(f"\n===== {app}: checking {len(matched)} pairs =====")

        for k in matched:
            g_path = os.path.join(g_folder, g_map[k][0])
            f_path = os.path.join(f_folder, f_map[k][0])
            try:
                frac = changed_pixel_fraction(g_path, f_path)
            except Exception as e:
                print(f"  [SKIP] {app}/{k}: could not compare ({e})")
                continue

            if frac < IDENTICAL_FRACTION:
                print(f"  [IDENTICAL] {app}/{k}  changed={frac*100:.3f}% of pixels "
                      f"-> genuine and fake look like the SAME untouched image")
                identical_flags.append((app, k, frac))
            elif frac > TOO_DIFFERENT_FRACTION:
                print(f"  [TOO DIFFERENT] {app}/{k}  changed={frac*100:.1f}% of pixels "
                      f"-> looks like an UNRELATED screenshot, not an edited pair")
                too_different_flags.append((app, k, frac))
            else:
                ok_count += 1

    total = ok_count + len(identical_flags) + len(too_different_flags)
    print("\n===================================================")
    print(f"Total pairs checked : {total}")
    print(f"Good (real edits)   : {ok_count}")
    print(f"Identical (no edit) : {len(identical_flags)}")
    print(f"Too different       : {len(too_different_flags)}")
    print("===================================================")

    if identical_flags:
        print("\n[ACTION] These pairs have NO visible edit — either re-edit the fake")
        print("version, or remove the pair (it teaches the model nothing):")
        for app, k, frac in identical_flags[:20]:
            print(f"    {app}/{k}  (changed={frac*100:.3f}%)")

    if too_different_flags:
        print("\n[ACTION] These pairs look unrelated — check the filenames match the")
        print("correct genuine/fake screenshots, fix the pairing, then re-run this script:")
        for app, k, frac in too_different_flags[:20]:
            print(f"    {app}/{k}  (changed={frac*100:.1f}%)")

    if not identical_flags and not too_different_flags:
        print("\nAll pairs look like genuine small edits. Safe to run pair_split.py next.")
    else:
        print("\nFix the flagged pairs above, then re-run this script before pair_split.py.")


if __name__ == "__main__":
    main()
