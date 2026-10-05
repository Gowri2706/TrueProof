# TrueProof — Fake UPI Payment Screenshot Detector

Final-year project: Android app + Python AI backend that detects whether a
UPI payment screenshot (GPay/PhonePe/Paytm/etc.) is GENUINE, FAKE, or
SUSPICIOUS, using OCR + Error Level Analysis (ELA) + a CNN model, combined
into one confidence score with explainable reasons.

**Honesty note (important for your viva):** no model is "100% accurate."
This pipeline is built to be *defensible*, not to claim perfection — it
shows a confidence score AND the exact evidence (OCR mismatch, ELA
tampering map, missing fields) so a human makes the final call. Say this
in your report; examiners respect it far more than an unrealistic 100%
accuracy claim.

---

## 1. Full folder structure (top to bottom)

```
TrueProof/
│
├── README.md                          <- this file
│
├── ui_mockup/
│   └── trueproof_ui.html              <- clickable UI mockup (open in any browser)
│
├── android/                           <- Android Studio project (open this folder in Android Studio)
│   ├── settings.gradle
│   ├── build.gradle
│   └── app/
│       ├── build.gradle
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── java/com/trueproof/app/
│           │   ├── MainActivity.java          <- Home screen
│           │   ├── ScanActivity.java          <- Upload/camera + calls AI server
│           │   ├── ResultActivity.java        <- Shows verdict + downloads PDF
│           │   ├── HistoryActivity.java       <- Local scan history (SQLite)
│           │   ├── ProfileActivity.java       <- Language + Dark mode + Help/About
│           │   ├── ApiClient.java             <- Retrofit setup (CHANGE BASE_URL here)
│           │   ├── ApiService.java            <- API endpoints
│           │   ├── ScanResponse.java          <- JSON response model
│           │   ├── DatabaseHelper.java        <- SQLite (local history)
│           │   ├── HistoryItem.java
│           │   ├── HistoryAdapter.java
│           │   ├── LocaleHelper.java          <- English/Tamil switching
│           │   └── TrueProofApplication.java  <- applies saved dark mode on launch
│           └── res/
│               ├── layout/                    <- activity_*.xml, item_*.xml
│               ├── values/strings.xml         <- English text
│               ├── values-ta/strings.xml      <- Tamil text
│               ├── values/themes.xml          <- Light theme
│               ├── values-night/themes.xml    <- Dark theme
│               ├── menu/bottom_nav_menu.xml
│               └── xml/file_paths.xml
│
└── python_ai/                         <- AI backend (run this on your laptop/server)
    ├── requirements.txt
    ├── api.py                         <- FastAPI server (Android talks to this)
    ├── report.py                      <- PDF report generator
    ├── ocr/
    │   └── ocr_utils.py               <- OCR + text rule checks
    ├── ela/
    │   └── ela_utils.py               <- Error Level Analysis (tampering detection)
    ├── model/
    │   ├── train.py                   <- TRAIN the CNN here
    │   ├── predict.py                 <- Combines CNN + ELA + OCR -> verdict
    │   ├── convert_tflite.py          <- optional: for on-device (offline) use
    │   ├── trueproof_model.h5         <- created after training (not included)
    │   └── training_history.png       <- created after training (not included)
    ├── dataset/
    │   ├── README.md                  <- how to collect/create data
    │   ├── genuine/                   <- YOU add real screenshots here
    │   ├── fake/                      <- YOU add fake/edited screenshots here
    │   └── tampered/                  <- optional extra fake data
    ├── uploads/                       <- images uploaded from the app land here (auto-created)
    └── reports/                       <- generated PDF reports (auto-created)
```

---

## 2. Step-by-step: set up and run the AI backend

Open a terminal in the `python_ai/` folder for ALL of these commands.

### Step 1 — Create a virtual environment
```bash
cd python_ai
python -m venv venv

# Activate it:
# Windows:
venv\Scripts\activate
# Mac/Linux:
source venv/bin/activate
```

### Step 2 — Install dependencies
```bash
pip install -r requirements.txt
```
This installs FastAPI, TensorFlow, OpenCV, EasyOCR, Pillow, scikit-learn,
reportlab, etc. First install can take 5-15 minutes (TensorFlow + EasyOCR
models are large).

### Step 3 — Add your dataset
Put images into:
```
dataset/genuine/   (real screenshots)
dataset/fake/      (edited/fake screenshots)
```
See `dataset/README.md` for exactly how to collect/create these.

### Step 4 — Train the CNN model
```bash
python model/train.py
```
This will:
- Print how many genuine/fake images were found
- Train for up to 25 epochs (stops early if it stops improving)
- Save the trained model to `model/trueproof_model.h5`
- Save an accuracy/loss graph to `model/training_history.png`
- Print a full classification report (precision/recall/F1) — **use this
  in your project report**, not just a single accuracy number.

### Step 5 — Test prediction on one image (before connecting the app)
```bash
python model/predict.py dataset/fake/some_fake_image.jpg
```
You should see JSON output with `verdict`, `confidence_percent`, `reasons`, etc.

### Step 6 — Start the API server
```bash
uvicorn api:app --host 0.0.0.0 --port 8000 --reload
```
Leave this running. Test it's alive by opening in a browser:
```
http://localhost:8000/health
```

### Step 7 — Find your laptop's IP address (so the phone can reach it)
```bash
# Windows:
ipconfig
# Mac/Linux:
ifconfig      (or: ip a)
```
Look for something like `192.168.1.5` (make sure the phone is on the SAME
wifi network as the laptop).

---

## 3. Step-by-step: set up and run the Android app

### Step 1 — Open the project
Open Android Studio → **Open** → select the `android/` folder.
Let Gradle sync (first sync downloads dependencies, may take a few minutes).

### Step 2 — Point the app at your AI server
Open `app/src/main/java/com/trueproof/app/ApiClient.java` and change:
```java
private static final String BASE_URL = "http://10.0.2.2:8000/";
```
- If testing on the Android **Emulator** → keep `10.0.2.2:8000` (this is
  the emulator's special address for your laptop's `localhost`).
- If testing on a **real phone** → change to `http://<your-laptop-IP>:8000/`
  (the IP you found in Step 7 above), e.g. `http://192.168.1.5:8000/`.

### Step 3 — Run the app
Connect your phone (enable USB debugging) or start an emulator, then click
**Run ▶** in Android Studio.

### Step 4 — Use the app
- **Home** → Upload Screenshot / Open Camera → goes to Scan screen
- **Scan** → pick image → tap Analyze → calls your FastAPI server
- **Result** → shows verdict, confidence, reasons → Download/Share PDF
- **History** → past scans stored locally in SQLite, searchable
- **Profile** → switch English ⇄ Tamil, toggle Dark Mode, Help, About, Exit

---

## 4. Improving accuracy (do this before your final submission)

1. **More data matters more than a fancier model.** 300+ images per class
   beats a complex architecture trained on 50 images.
2. Balance your classes (roughly equal genuine vs fake counts).
3. Create fake samples yourself (see `dataset/README.md`) so you can
   explain in the viva exactly how each one was made.
4. Re-run `python model/train.py` after adding data — accuracy should improve.
5. Look at `model/training_history.png` — if validation accuracy is much
   lower than training accuracy, that's overfitting → add more data or
   more augmentation.
6. Read the printed classification report (precision/recall per class),
   not just overall accuracy — this is what a real evaluator checks.

---

## 5. What to say in your report/viva about accuracy

Instead of claiming "100% accurate," write something like:

> "The system combines three independent signals — OCR-based rule checks,
> Error Level Analysis for pixel-level tampering, and a CNN trained on
> [N] genuine and [N] fake screenshots — into a weighted confidence score.
> On the validation set, the model achieved [X]% accuracy with precision
> [P] and recall [R] for the fake class. Because no single signal is
> perfect, the app also shows the underlying evidence so a human can make
> the final call on borderline (SUSPICIOUS) cases."

This is honest, technically sound, and will hold up far better under
questioning than an unqualified "100% correct" claim.
