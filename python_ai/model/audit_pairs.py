"""
audit_pairs.py
-----------------
Verifies your new pair-based dataset is complete and correct BEFORE you
split it into train/val/test.

Checks, for each app (Gpay, Phonepe, Paytm):
  - Every genuine/<App>/<id>.png has a matching fake/<App>/<id>.png
    (matching by "pair key" — the filename with any trailing
    "_original" / "_edit" removed, so both "1.png"/"1.png" and
    "001_original.png"/"001_edit.png" naming styles work)
  - Flags genuine screenshots with NO fake counterpart
  - Flags fake screenshots with NO genuine counterpart (these would be
    "random unpaired fakes" — exactly what your new design must avoid)
  - Flags duplicate pair keys within the same class/app

Run (from python_ai/ folder):
    python model/audit_pairs.py
"""

import os
import re
from collections import defaultdict

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, "dataset")
APPS = ["Gpay", "Phonepe", "Paytm"]
EXTS = (".png", ".jpg", ".jpeg")


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
    """Returns {pair_key: [filenames]} for one class/app folder."""
    out = defaultdict(list)
    if not os.path.isdir(folder):
        return out
    for f in os.listdir(folder):
        if f.lower().endswith(EXTS):
            out[pair_key(f)].append(f)
    return out


def main():
    total_genuine = 0
    total_fake = 0
    total_pairs = 0
    problems = []

    for app in APPS:
        g_folder = os.path.join(DATASET_DIR, "genuine", app)
        f_folder = os.path.join(DATASET_DIR, "fake", app)

        g_map = scan(g_folder)
        f_map = scan(f_folder)

        g_keys = set(g_map.keys())
        f_keys = set(f_map.keys())

        matched = g_keys & f_keys
        genuine_only = g_keys - f_keys
        fake_only = f_keys - g_keys

        print(f"\n===== {app} =====")
        print(f"genuine images: {sum(len(v) for v in g_map.values())}")
        print(f"fake images:    {sum(len(v) for v in f_map.values())}")
        print(f"matched pairs:  {len(matched)}")

        if genuine_only:
            print(f"[PROBLEM] {len(genuine_only)} genuine screenshot(s) with NO fake pair:")
            for k in sorted(genuine_only)[:10]:
                print(f"    {g_map[k]}")
            if len(genuine_only) > 10:
                print(f"    ... and {len(genuine_only) - 10} more")
            problems.append(f"{app}: {len(genuine_only)} unpaired genuine images")

        if fake_only:
            print(f"[PROBLEM] {len(fake_only)} fake screenshot(s) with NO genuine pair "
                  f"(these look like random unpaired fakes — remove or pair them):")
            for k in sorted(fake_only)[:10]:
                print(f"    {f_map[k]}")
            if len(fake_only) > 10:
                print(f"    ... and {len(fake_only) - 10} more")
            problems.append(f"{app}: {len(fake_only)} unpaired fake images")

        for k, files in g_map.items():
            if len(files) > 1:
                print(f"[WARNING] duplicate pair key '{k}' in genuine/{app}: {files}")
        for k, files in f_map.items():
            if len(files) > 1:
                print(f"[WARNING] duplicate pair key '{k}' in fake/{app}: {files}")

        total_genuine += sum(len(v) for v in g_map.values())
        total_fake += sum(len(v) for v in f_map.values())
        total_pairs += len(matched)

    print("\n===================================================")
    print(f"TOTAL genuine images : {total_genuine}")
    print(f"TOTAL fake images    : {total_fake}")
    print(f"TOTAL matched pairs  : {total_pairs}")
    print("===================================================")

    if problems:
        print("\n[ACTION NEEDED] Fix these before splitting:")
        for p in problems:
            print(f"  - {p}")
        print("\nDo NOT run pair_split.py until this list is empty.")
    else:
        print("\nAll clear! Every genuine screenshot has exactly one fake pair.")
        print("Next: python model/pair_split.py")


if __name__ == "__main__":
    main()
