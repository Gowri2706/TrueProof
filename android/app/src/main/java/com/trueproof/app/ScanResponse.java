package com.trueproof.app;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Mirrors the JSON structure returned by python_ai/model/predict.py
 * via the /scan endpoint in api.py.
 */
public class ScanResponse {

    @SerializedName("scan_id")
    public String scanId;

    public String verdict; // "FAKE" | "SUSPICIOUS" | "GENUINE"

    @SerializedName("confidence_percent")
    public double confidencePercent;

    @SerializedName("fake_score")
    public double fakeScore;

    public List<String> reasons;

    public OcrFields ocr;

    @SerializedName("ela_score")
    public double elaScore;

    @SerializedName("cnn_genuine_probability")
    public double cnnGenuineProbability;

    public static class OcrFields {
        public String amount;
        @SerializedName("upi_id")
        public String upiId;
        @SerializedName("transaction_id")
        public String transactionId;
        public String date;
        public String status;
    }
}
