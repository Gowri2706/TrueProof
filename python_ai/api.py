"""
api.py
------
FastAPI backend. This is what the Android app talks to.

RUN THIS (from python_ai/ folder, after activating your venv):
    uvicorn api:app --host 0.0.0.0 --port 8000 --reload

Then on your phone (same wifi as your laptop), the Android app should call:
    http://<your-laptop-lan-ip>:8000/scan
e.g. http://192.168.1.5:8000/scan  (find your IP with `ipconfig`/`ifconfig`)

If you're testing on an Android EMULATOR (not a real phone), use:
    http://10.0.2.2:8000/scan

ENDPOINTS:
    POST /scan            -> upload image, get JSON prediction
    GET  /report/{scan_id}-> download PDF report for a past scan
    GET  /health           -> simple check that the server is alive
"""

import os
import uuid
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware

from model.predict import full_predict
from report import generate_pdf_report

UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "uploads")
REPORT_DIR = os.path.join(os.path.dirname(__file__), "reports")
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(REPORT_DIR, exist_ok=True)

app = FastAPI(title="TrueProof AI API")

# Allow the Android app (and browser testing) to call this freely.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# in-memory store of scan_id -> result, for the /report endpoint.
# For a real deployed app, replace with a proper DB (SQLite is fine).
_scan_cache = {}


@app.get("/health")
def health():
    return {"status": "ok", "message": "TrueProof AI server is running"}


@app.post("/scan")
async def scan_screenshot(file: UploadFile = File(...)):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Please upload an image file (jpg/png).")

    scan_id = str(uuid.uuid4())[:8]
    ext = os.path.splitext(file.filename)[1] or ".jpg"
    saved_path = os.path.join(UPLOAD_DIR, f"{scan_id}{ext}")

    with open(saved_path, "wb") as f:
        f.write(await file.read())

    try:
        result = full_predict(saved_path)
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {e}")

    result["scan_id"] = scan_id
    _scan_cache[scan_id] = {"result": result, "image_path": saved_path}

    return JSONResponse(result)


@app.get("/report/{scan_id}")
def get_report(scan_id: str):
    if scan_id not in _scan_cache:
        raise HTTPException(status_code=404, detail="Scan not found. Scan may have expired or server restarted.")

    entry = _scan_cache[scan_id]
    pdf_path = os.path.join(REPORT_DIR, f"{scan_id}.pdf")
    if not os.path.exists(pdf_path):
        generate_pdf_report(entry["result"], entry["image_path"], pdf_path)

    return FileResponse(pdf_path, media_type="application/pdf", filename=f"TrueProof_Report_{scan_id}.pdf")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
