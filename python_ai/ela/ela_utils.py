"""
ela_utils.py
------------
Error Level Analysis (ELA).

WHY THIS WORKS:
When a genuine photo/screenshot is saved as JPEG, every 8x8 block compresses
to roughly the SAME error level. When someone edits part of the image
(changes the amount, pastes a fake "success" tick, etc.) and re-saves it,
the EDITED region compresses differently from the untouched region because
it has already been through one extra round of JPEG compression at a
different quality. ELA re-compresses the image at a known quality and
diffs it against the original — the edited region "lights up" brighter
than the rest.

NOTE: PNG screenshots (no prior JPEG compression) give a weaker ELA signal.
That's why ELA is combined with OCR + metadata checks, not used alone.
"""

import os
import numpy as np
from PIL import Image, ImageChops, ImageEnhance

ELA_QUALITY = 90          # JPEG re-save quality used for comparison
ELA_SCALE = 15            # brightness multiplier so the diff is visible


def generate_ela_image(image_path: str, output_path: str = None, quality: int = ELA_QUALITY):
    """
    Returns (ela_image: PIL.Image, ela_score: float 0-100)
    ela_score is a single tampering-likelihood number derived from how much
    the image "lit up" after recompression. Higher = more likely edited.
    """
    original = Image.open(image_path).convert("RGB")

    tmp_path = image_path + ".ela_tmp.jpg"
    original.save(tmp_path, "JPEG", quality=quality)
    resaved = Image.open(tmp_path)

    ela_image = ImageChops.difference(original, resaved)

    extrema = ela_image.getextrema()
    max_diff = max([ex[1] for ex in extrema]) or 1
    scale = 255.0 / max_diff
    ela_image = ImageEnhance.Brightness(ela_image).enhance(scale)

    # Numeric score: mean brightness of the ELA diff, normalized 0-100.
    arr = np.array(ela_image.convert("L"), dtype=np.float32)
    ela_score = float(np.clip((arr.mean() / 255.0) * 100 * (ELA_SCALE / 10), 0, 100))

    os.remove(tmp_path)

    if output_path:
        ela_image.save(output_path)

    return ela_image, round(ela_score, 2)


def get_tampering_regions(ela_image: Image.Image, threshold: int = 60):
    """
    Returns a list of bounding boxes [(x1,y1,x2,y2), ...] for regions
    of the ELA image that are brighter than `threshold` (0-255) — i.e.
    likely-edited regions. Uses simple thresholding + connected components
    via OpenCV (kept lightweight, no extra heavy deps).
    """
    import cv2

    gray = np.array(ela_image.convert("L"))
    _, binary = cv2.threshold(gray, threshold, 255, cv2.THRESH_BINARY)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    boxes = []
    for c in contours:
        x, y, w, h = cv2.boundingRect(c)
        if w * h > 150:  # ignore tiny noise specks
            boxes.append((x, y, x + w, y + h))
    return boxes


if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("Usage: python ela_utils.py <image_path>")
        sys.exit(1)
    img, score = generate_ela_image(sys.argv[1], "ela_output.jpg")
    print(f"ELA tampering score: {score}/100  (saved ela_output.jpg)")
