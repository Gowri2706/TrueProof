package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzingActivity extends AppCompatActivity {

    private static final String TAG = "TRUESHOT_DEBUG";

    private TextView tvStep1;
    private TextView tvStep2;
    private TextView tvStep3;
    private TextView tvStep4;

    private View stepDot1;
    private View stepDot2;
    private View stepDot3;
    private View stepDot4;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private int currentStep = 0;

    private String imagePath;

    private TFLiteClassifier classifier;
    private TextRecognizer textRecognizer;

    private TFLiteClassifier.Result analysisResult;

    private String extractedAmount = null;

    private long analysisStartTime;

    private boolean analysisFinished = false;
    private boolean resultOpened = false;

    private static final long MIN_ANALYSIS_TIME = 5000L;

    // =========================================================
    // CONTEXT
    // =========================================================

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_analyzing);

        imagePath =
                getIntent().getStringExtra("image_path");

        Log.d(TAG, "=================================");
        Log.d(TAG, "ANALYZING SCREEN STARTED");
        Log.d(TAG, "Image path = " + imagePath);
        Log.d(TAG, "=================================");

        tvStep1 = findViewById(R.id.tvStep1);
        tvStep2 = findViewById(R.id.tvStep2);
        tvStep3 = findViewById(R.id.tvStep3);
        tvStep4 = findViewById(R.id.tvStep4);

        stepDot1 = findViewById(R.id.stepDot1);
        stepDot2 = findViewById(R.id.stepDot2);
        stepDot3 = findViewById(R.id.stepDot3);
        stepDot4 = findViewById(R.id.stepDot4);

        TextView backButton =
                findViewById(R.id.btnAnalyzingBack);

        backButton.setOnClickListener(
                v -> finish()
        );

        analysisStartTime =
                System.currentTimeMillis();

        startAnalysisAnimation();

        startRealAnalysis();
    }

    // =========================================================
    // VISUAL ANALYSIS ANIMATION
    // =========================================================

    private void startAnalysisAnimation() {

        currentStep = 0;

        updateSteps();

        handler.postDelayed(
                () -> {
                    currentStep = 1;
                    updateSteps();
                },
                1000
        );

        handler.postDelayed(
                () -> {
                    currentStep = 2;
                    updateSteps();
                },
                2200
        );

        handler.postDelayed(
                () -> {
                    currentStep = 3;
                    updateSteps();
                },
                3400
        );

        handler.postDelayed(
                () -> {
                    currentStep = 4;
                    updateSteps();
                },
                4300
        );
    }

    // =========================================================
    // UPDATE STEP UI
    // =========================================================

    private void updateSteps() {

        if (currentStep >= 1) {

            stepDot1.setBackgroundResource(
                    R.drawable.bg_step_active
            );

            tvStep1.setTextColor(
                    getColor(R.color.text)
            );
        }

        if (currentStep >= 2) {

            stepDot2.setBackgroundResource(
                    R.drawable.bg_step_active
            );

            tvStep2.setTextColor(
                    getColor(R.color.text)
            );
        }

        if (currentStep >= 3) {

            stepDot3.setBackgroundResource(
                    R.drawable.bg_step_active
            );

            tvStep3.setTextColor(
                    getColor(R.color.text)
            );
        }

        if (currentStep >= 4) {

            stepDot4.setBackgroundResource(
                    R.drawable.bg_step_active
            );

            tvStep4.setTextColor(
                    getColor(R.color.text)
            );
        }
    }

    // =========================================================
    // START REAL AI ANALYSIS
    // =========================================================

    private void startRealAnalysis() {

        Log.d(TAG, "AI START");

        if (imagePath == null
                || imagePath.trim().isEmpty()) {

            Log.e(TAG,
                    "ERROR: Image path is empty"
            );

            showError("Image not found");

            return;
        }

        File imageFile =
                new File(imagePath);

        Log.d(TAG,
                "Image exists = "
                        + imageFile.exists()
                        + " | size = "
                        + imageFile.length()
                        + " bytes"
        );

        if (!imageFile.exists()) {

            Log.e(TAG,
                    "ERROR: Image file does not exist"
            );

            showError(
                    "Selected image is unavailable"
            );

            return;
        }

        // -----------------------------------------------------
        // LOAD AI MODEL + OCR
        // -----------------------------------------------------

        try {

            Log.d(TAG,
                    "Loading TFLite model..."
            );

            classifier =
                    new TFLiteClassifier(this);

            Log.d(TAG,
                    "TFLite model loaded"
            );

            Log.d(TAG,
                    "Loading ML Kit OCR..."
            );

            textRecognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                    );

            Log.d(TAG,
                    "ML Kit OCR loaded"
            );

        } catch (IOException e) {

            Log.e(TAG,
                    "ERROR: Could not load AI/OCR",
                    e
            );

            showError(
                    "AI model could not be loaded"
            );

            return;
        }

        // -----------------------------------------------------
        // RUN AI IN BACKGROUND
        // -----------------------------------------------------

        new Thread(
                () -> runAIAnalysis(imageFile)
        ).start();
    }

    // =========================================================
    // RUN TFLITE
    // =========================================================

    private void runAIAnalysis(
            File imageFile
    ) {

        Log.d(TAG,
                "AI THREAD START"
        );

        Bitmap bitmap =
                BitmapFactory.decodeFile(
                        imageFile.getAbsolutePath()
                );

        if (bitmap == null) {

            Log.e(TAG,
                    "ERROR: Bitmap decode failed"
            );

            showError(
                    "Could not decode image"
            );

            return;
        }

        Log.d(TAG,
                "Bitmap decoded: "
                        + bitmap.getWidth()
                        + " x "
                        + bitmap.getHeight()
        );

        try {

            // -------------------------------------------------
            // TFLITE CLASSIFICATION
            // -------------------------------------------------

            Log.d(TAG,
                    "TFLite classification START"
            );

            analysisResult =
                    classifier.classify(bitmap);

            Log.d(TAG,
                    "TFLite classification DONE"
            );

            Log.d(TAG,
                    "Verdict = "
                            + analysisResult.verdict
            );

            Log.d(TAG,
                    "Probability genuine = "
                            + analysisResult.probabilityGenuine
            );

            Log.d(TAG,
                    "Confidence = "
                            + analysisResult.confidence
            );

            bitmap.recycle();

            // -------------------------------------------------
            // MOVE TO OCR
            // -------------------------------------------------

            Log.d(TAG,
                    "Moving from AI -> OCR"
            );

            runOnUiThread(
                    this::runOCR
            );

        } catch (Exception e) {

            Log.e(TAG,
                    "ERROR: TFLite classification failed",
                    e
            );

            bitmap.recycle();

            showError(
                    "AI analysis failed"
            );
        }
    }

    // =========================================================
    // OCR
    // =========================================================

    private void runOCR() {

        Log.d(TAG,
                "================================="
        );

        Log.d(TAG,
                "OCR START"
        );

        Log.d(TAG,
                "================================="
        );

        if (textRecognizer == null) {

            Log.e(TAG,
                    "ERROR: textRecognizer is NULL"
            );

            finishAnalysis();

            return;
        }

        File imageFile =
                new File(imagePath);

        Log.d(TAG,
                "OCR image exists = "
                        + imageFile.exists()
        );

        if (!imageFile.exists()) {

            Log.e(TAG,
                    "ERROR: OCR image file missing"
            );

            finishAnalysis();

            return;
        }

        try {

            InputImage image =
                    InputImage.fromFilePath(
                            this,
                            Uri.fromFile(imageFile)
                    );

            Log.d(TAG,
                    "InputImage created successfully"
            );

            Log.d(TAG,
                    "Sending image to ML Kit..."
            );

            textRecognizer
                    .process(image)

                    .addOnSuccessListener(
                            text -> {

                                Log.d(TAG,
                                        "OCR SUCCESS"
                                );

                                String fullText =
                                        text.getText();

                                if (fullText == null) {

                                    Log.e(TAG,
                                            "OCR returned NULL text"
                                    );

                                } else {

                                    Log.d(TAG,
                                            "OCR TEXT START"
                                    );

                                    Log.d(TAG,
                                            fullText
                                    );

                                    Log.d(TAG,
                                            "OCR TEXT END"
                                    );
                                }

                                // ---------------------------------
                                // EXTRACT AMOUNT
                                // ---------------------------------

                                extractedAmount =
                                        extractAmount(text);

                                if (extractedAmount != null) {

                                    Log.d(TAG,
                                            "AMOUNT FOUND = ₹"
                                                    + extractedAmount
                                    );

                                } else {

                                    Log.e(TAG,
                                            "AMOUNT NOT FOUND"
                                    );
                                }

                                finishAnalysis();
                            }
                    )

                    .addOnFailureListener(
                            e -> {

                                Log.e(TAG,
                                        "OCR FAILED",
                                        e
                                );

                                extractedAmount = null;

                                finishAnalysis();
                            }
                    );

        } catch (IOException e) {

            Log.e(TAG,
                    "ERROR: Could not create InputImage",
                    e
            );

            extractedAmount = null;

            finishAnalysis();
        }
    }

    // =========================================================
    // EXTRACT AMOUNT
    // =========================================================

    private String extractAmount(
            Text text
    ) {

        if (text == null) {

            Log.e(TAG,
                    "extractAmount(): Text is NULL"
            );

            return null;
        }

        String fullText =
                text.getText();

        if (fullText == null
                || fullText.trim().isEmpty()) {

            Log.e(TAG,
                    "extractAmount(): OCR text empty"
            );

            return null;
        }

        Log.d(TAG,
                "Amount extraction started"
        );

        // -----------------------------------------------------
        // NORMALIZE OCR TEXT
        // -----------------------------------------------------

        String normalizedText =
                fullText
                        .replace('\n', ' ')
                        .replace('\r', ' ')
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        Log.d(TAG,
                "Normalized OCR text = "
                        + normalizedText
        );

        // =====================================================
        // 1. EXPLICIT CURRENCY
        //
        // ₹500
        // ₹ 500
        // ₹1,250
        // ₹ 1,250.00
        // Rs 500
        // Rs. 500
        // INR 500
        // =====================================================

        Pattern currencyPattern =
                Pattern.compile(
                        "(₹|Rs\\.?|INR)\\s*"
                                + "([0-9][0-9,]*"
                                + "(?:\\.[0-9]{1,2})?)",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher currencyMatcher =
                currencyPattern.matcher(
                        normalizedText
                );

        if (currencyMatcher.find()) {

            String amount =
                    cleanAmount(
                            currencyMatcher.group(2)
                    );

            if (amount != null) {

                Log.d(TAG,
                        "Currency amount accepted = ₹"
                                + amount
                );

                return amount;
            }
        }

        // =====================================================
        // 2. LABEL PATTERN
        //
        // Amount: 4400
        // Paid 4400
        // Sent 4400
        // Amount ₹4400
        // =====================================================

        Pattern labeledAmountPattern =
                Pattern.compile(
                        "(?:amount|paid|sent)"
                                + "\\s*[:\\-]?\\s*"
                                + "(?:₹|Rs\\.?|INR)?\\s*"
                                + "([0-9][0-9,]*"
                                + "(?:\\.[0-9]{1,2})?)",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher labeledMatcher =
                labeledAmountPattern.matcher(
                        normalizedText
                );

        if (labeledMatcher.find()) {

            String amount =
                    cleanAmount(
                            labeledMatcher.group(1)
                    );

            if (amount != null) {

                Log.d(TAG,
                        "Label amount accepted = ₹"
                                + amount
                );

                return amount;
            }
        }

        // =====================================================
        // 3. GOOGLE PAY SUCCESS SCREEN
        //
        // PDF OCR example:
        //
        // To VIKASHINI S T 50 23 Jul 2026, 7:11am
        // ...
        // Completed Google Pay
        // ...
        // Share 50
        //
        // The FIRST 50 belongs to the transaction.
        // The 50 after Share is ignored.
        // =====================================================

        String lowerText =
                normalizedText.toLowerCase(Locale.US);

        if (lowerText.contains("completed google pay")) {

            Log.d(TAG,
                    "Google Pay success screen detected"
            );

            // -------------------------------------------------
            // Pattern:
            //
            // VIKASHINI S T 50 23 Jul 2026
            //
            // Capture the number immediately before
            // the transaction date.
            // -------------------------------------------------

            Pattern gpayAmountPattern =
                    Pattern.compile(
                            "\\b([0-9]{1,6}"
                                    + "(?:,[0-9]{3})*"
                                    + "(?:\\.[0-9]{1,2})?)"
                                    + "\\s+"
                                    + "\\d{1,2}"
                                    + "\\s+"
                                    + "[A-Za-z]{3}"
                                    + "\\s+"
                                    + "\\d{4}"
                    );

            Matcher gpayMatcher =
                    gpayAmountPattern.matcher(
                            normalizedText
                    );

            if (gpayMatcher.find()) {

                String amount =
                        gpayMatcher.group(1);

                Log.d(TAG,
                        "Google Pay date-adjacent amount = "
                                + amount
                );

                String cleanedAmount =
                        cleanAmount(amount);

                if (cleanedAmount != null) {

                    Log.d(TAG,
                            "Google Pay amount ACCEPTED = ₹"
                                    + cleanedAmount
                    );

                    return cleanedAmount;
                }
            }

            // -------------------------------------------------
            // Second GPay pattern:
            //
            // To: VIKASHINI S T 50 23 Jul 2026
            // -------------------------------------------------

            Pattern gpayToPattern =
                    Pattern.compile(
                            "To:?\\s*"
                                    + "[A-Za-z][A-Za-z0-9 .]{1,40}"
                                    + "\\s+"
                                    + "([0-9]{1,6}"
                                    + "(?:,[0-9]{3})*"
                                    + "(?:\\.[0-9]{1,2})?)"
                                    + "\\s+"
                                    + "\\d{1,2}"
                                    + "\\s+"
                                    + "[A-Za-z]{3}"
                                    + "\\s+"
                                    + "\\d{4}",
                            Pattern.CASE_INSENSITIVE
                    );

            Matcher gpayToMatcher =
                    gpayToPattern.matcher(
                            normalizedText
                    );

            if (gpayToMatcher.find()) {

                String amount =
                        gpayToMatcher.group(1);

                Log.d(TAG,
                        "Google Pay To-pattern amount = "
                                + amount
                );

                String cleanedAmount =
                        cleanAmount(amount);

                if (cleanedAmount != null) {

                    Log.d(TAG,
                            "Google Pay To-pattern ACCEPTED = ₹"
                                    + cleanedAmount
                    );

                    return cleanedAmount;
                }
            }
        }

        // =====================================================
        // 4. FORMATTED INDIAN AMOUNT
        //
        // 1,000
        // 4,400
        // 12,500
        // 1,25,000
        // =====================================================

        Pattern formattedIndianAmountPattern =
                Pattern.compile(
                        "\\b(?:"
                                + "[0-9]{1,3},[0-9]{3}"
                                + "|"
                                + "[0-9]{1,2},[0-9]{2},[0-9]{3}"
                                + ")"
                                + "(?:\\.[0-9]{1,2})?\\b"
                );

        Matcher formattedMatcher =
                formattedIndianAmountPattern.matcher(
                        normalizedText
                );

        if (formattedMatcher.find()) {

            String amount =
                    cleanAmount(
                            formattedMatcher.group()
                    );

            if (amount != null) {

                Log.d(TAG,
                        "Formatted Indian amount accepted = ₹"
                                + amount
                );

                return amount;
            }
        }

        // =====================================================
        // NOTHING VERIFIED
        // =====================================================

        Log.e(TAG,
                "NO VERIFIED AMOUNT FOUND"
        );

        return null;
    }

    // =========================================================
    // CLEAN AMOUNT
    // =========================================================

    private String cleanAmount(
            String amount
    ) {

        if (amount == null) {
            return null;
        }

        amount =
                amount
                        .replace(",", "")
                        .trim();

        try {

            double value =
                    Double.parseDouble(
                            amount
                    );

            if (value < 0) {
                return null;
            }

            if (value == Math.floor(value)) {

                return String.format(
                        Locale.US,
                        "%.0f",
                        value
                );

            } else {

                return String.format(
                        Locale.US,
                        "%.2f",
                        value
                );
            }

        } catch (NumberFormatException e) {

            Log.e(TAG,
                    "Invalid amount = "
                            + amount
            );

            return null;
        }
    }

    // =========================================================
    // ANALYSIS FINISHED
    // =========================================================

    private void finishAnalysis() {

        Log.d(TAG,
                "finishAnalysis() called"
        );

        analysisFinished = true;

        long elapsed =
                System.currentTimeMillis()
                        - analysisStartTime;

        long remaining =
                MIN_ANALYSIS_TIME
                        - elapsed;

        if (remaining < 0) {
            remaining = 0;
        }

        Log.d(TAG,
                "Analysis elapsed = "
                        + elapsed
                        + " ms"
        );

        Log.d(TAG,
                "Waiting remaining = "
                        + remaining
                        + " ms"
        );

        handler.postDelayed(
                this::openResult,
                remaining
        );
    }

    // =========================================================
    // OPEN RESULT
    // =========================================================

    private void openResult() {

        Log.d(TAG,
                "================================="
        );

        Log.d(TAG,
                "OPEN RESULT"
        );

        if (resultOpened) {

            Log.d(TAG,
                    "Result already opened"
            );

            return;
        }

        if (!analysisFinished) {

            Log.e(TAG,
                    "ERROR: analysisFinished = false"
            );

            return;
        }

        if (analysisResult == null) {

            Log.e(TAG,
                    "ERROR: analysisResult = NULL"
            );

            showError(
                    "Could not complete analysis"
            );

            return;
        }

        resultOpened = true;

        String scanId =
                "TP-" + System.currentTimeMillis();

        double confidencePercent =
                analysisResult.confidence
                        * 100.0;

        Log.d(TAG,
                "Scan ID = "
                        + scanId
        );

        Log.d(TAG,
                "Final verdict = "
                        + analysisResult.verdict
        );

        Log.d(TAG,
                "Final confidence = "
                        + confidencePercent
        );

        Log.d(TAG,
                "Final extracted amount = "
                        + extractedAmount
        );

        // =====================================================
        // SAVE TO DATABASE
        // =====================================================

        DatabaseHelper db =
                new DatabaseHelper(
                        AnalyzingActivity.this
                );

        HistoryItem item =
                new HistoryItem();

        item.scanId =
                scanId;

        item.amount =
                extractedAmount;

        item.upiId =
                null;

        item.verdict =
                analysisResult.verdict;

        item.confidence =
                confidencePercent;

        item.timestamp =
                System.currentTimeMillis();

        item.imagePath =
                imagePath;

        Log.d(TAG,
                "DATABASE SAVE START"
        );

        Log.d(TAG,
                "DB amount = "
                        + item.amount
        );

        Log.d(TAG,
                "DB verdict = "
                        + item.verdict
        );

        db.insertScan(item);

        Log.d(TAG,
                "DATABASE SAVE DONE"
        );

        // =====================================================
        // OPEN RESULT ACTIVITY
        // =====================================================

        Intent intent =
                new Intent(
                        AnalyzingActivity.this,
                        ResultActivity.class
                );

        intent.putExtra(
                "scan_id",
                scanId
        );

        intent.putExtra(
                "verdict",
                analysisResult.verdict
        );

        intent.putExtra(
                "confidence",
                confidencePercent
        );

        intent.putExtra(
                "reasons",
                "On-device AI image analysis"
        );

        intent.putExtra(
                "image_path",
                imagePath
        );

        if (extractedAmount != null) {

            intent.putExtra(
                    "amount",
                    extractedAmount
            );
        }

        Log.d(TAG,
                "Opening ResultActivity"
        );

        startActivity(intent);

        finish();
    }

    // =========================================================
    // ERROR
    // =========================================================

    private void showError(
            String message
    ) {

        Log.e(TAG,
                "SHOW ERROR = "
                        + message
        );

        runOnUiThread(
                () -> {

                    Toast.makeText(
                            AnalyzingActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show();

                    finish();
                }
        );
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {

        Log.d(TAG,
                "AnalyzingActivity destroyed"
        );

        handler.removeCallbacksAndMessages(
                null
        );

        if (classifier != null) {

            classifier.close();

            classifier = null;
        }

        if (textRecognizer != null) {

            textRecognizer.close();

            textRecognizer = null;
        }

        super.onDestroy();
    }
}