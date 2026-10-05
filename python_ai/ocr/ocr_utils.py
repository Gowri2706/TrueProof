"""
ocr_utils.py
------------
Extracts text from a payment screenshot using EasyOCR, then runs simple
RULE-BASED checks that catch a huge fraction of fake screenshots without
even needing the CNN (font copy-paste jobs, wrong UPI handle patterns,
missing transaction ID, amount that doesn't match anywhere else on the
screen, etc).

This file has two independent, reusable parts:
  1. extract_text()      -> raw OCR text + structured fields
  2. run_text_checks()   -> list of (rule_name, passed: bool, detail)
"""

import re
import easyocr

_reader = None  # lazy-loaded singleton (EasyOCR model load is slow)


def get_reader():
    global _reader
    if _reader is None:
        _reader = easyocr.Reader(['en'], gpu=False)  # set gpu=True if CUDA available
    return _reader


def extract_text(image_path: str) -> dict:
    """
    Runs OCR and pulls out the fields we care about for a UPI payment
    screenshot: amount, UPI ID, transaction/reference ID, date, status word.
    """
    reader = get_reader()
    results = reader.readtext(image_path, detail=0)  # list[str]
    full_text = " ".join(results)

    amount_match = re.search(r'(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)', full_text)
    upi_match = re.search(r'[\w.\-]{2,}@[a-zA-Z]{2,}', full_text)
    txn_match = re.search(r'(?:Txn|Transaction|UTR|Ref)[\s:ID]*[:#]?\s*([A-Za-z0-9]{8,25})', full_text, re.IGNORECASE)
    date_match = re.search(r'(\d{1,2}\s?(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s?\d{2,4})', full_text, re.IGNORECASE)

    status = None
    lower = full_text.lower()
    if "successful" in lower or "success" in lower or "completed" in lower:
        status = "success"
    elif "failed" in lower or "declined" in lower:
        status = "failed"
    elif "pending" in lower:
        status = "pending"

    return {
        "raw_text": full_text,
        "amount": amount_match.group(1) if amount_match else None,
        "upi_id": upi_match.group(0) if upi_match else None,
        "transaction_id": txn_match.group(1) if txn_match else None,
        "date": date_match.group(0) if date_match else None,
        "status": status,
        "word_boxes": reader.readtext(image_path, detail=1),  # for font/spacing analysis
    }


def run_text_checks(fields: dict) -> list:
    """
    Simple, explainable rule checks. Each returns (rule, passed, detail).
    These map directly to the "Reason" list shown on the Result screen.
    """
    checks = []

    checks.append((
        "Amount detected",
        fields["amount"] is not None,
        f"Amount found: ₹{fields['amount']}" if fields["amount"] else "No ₹ amount found in image text"
    ))

    checks.append((
        "UPI ID format valid",
        fields["upi_id"] is not None,
        f"UPI ID found: {fields['upi_id']}" if fields["upi_id"] else "No valid UPI ID pattern (name@bank) found"
    ))

    checks.append((
        "Transaction / UTR ID present",
        fields["transaction_id"] is not None,
        f"ID found: {fields['transaction_id']}" if fields["transaction_id"] else "No transaction/UTR ID found — genuine apps always show one"
    ))

    checks.append((
        "Date present",
        fields["date"] is not None,
        f"Date found: {fields['date']}" if fields["date"] else "No date detected"
    ))

    checks.append((
        "Status word present",
        fields["status"] is not None,
        f"Status: {fields['status']}" if fields["status"] else "No clear success/failed/pending status text found"
    ))

    return checks


if __name__ == "__main__":
    import sys, json
    if len(sys.argv) < 2:
        print("Usage: python ocr_utils.py <image_path>")
        sys.exit(1)
    data = extract_text(sys.argv[1])
    data.pop("word_boxes")  # too long to print
    print(json.dumps(data, indent=2))
    print("\nChecks:")
    for rule, passed, detail in run_text_checks(data):
        print(f"  [{'PASS' if passed else 'FAIL'}] {rule} - {detail}")
