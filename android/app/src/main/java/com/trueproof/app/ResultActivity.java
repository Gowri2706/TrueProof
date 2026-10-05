package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    private String imagePath;
    private String verdict;
    private double confidence;
    private String reasonsRaw;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_result);

        ImageView imgResult =
                findViewById(R.id.imgResult);

        TextView tvPrediction =
                findViewById(R.id.tvPrediction);

        TextView tvConfidence =
                findViewById(R.id.tvConfidence);

        android.widget.LinearLayout reasonsContainer =
                findViewById(R.id.reasonsContainer);

        Button btnDownload =
                findViewById(R.id.btnDownloadReport);

        Button btnScanAgain =
                findViewById(R.id.btnScanAgain);

        // --------------------------------------------------
        // GET ANALYZED IMAGE
        // --------------------------------------------------

        imagePath =
                getIntent().getStringExtra("image_path");

        // --------------------------------------------------
        // GET VERDICT
        // --------------------------------------------------

        verdict =
                getIntent().getStringExtra("verdict");

        if (verdict == null
                || verdict.trim().isEmpty()) {

            verdict = "UNKNOWN";
        }

        // --------------------------------------------------
        // GET CONFIDENCE
        // --------------------------------------------------

        confidence =
                getIntent().getDoubleExtra(
                        "confidence",
                        0
                );

        // --------------------------------------------------
        // GET AI REASONS
        // --------------------------------------------------

        reasonsRaw =
                getIntent().getStringExtra("reasons");

        // --------------------------------------------------
        // SHOW ANALYZED IMAGE
        // --------------------------------------------------

        if (imagePath != null
                && !imagePath.trim().isEmpty()) {

            try {

                imgResult.setImageBitmap(
                        BitmapFactory.decodeFile(
                                imagePath
                        )
                );

            } catch (Exception e) {

                imgResult.setImageDrawable(null);
            }
        }

        // --------------------------------------------------
        // SHOW VERDICT
        // --------------------------------------------------

        tvPrediction.setText(
                verdict
        );

        int color;

        switch (verdict) {

            case "EDITED":

                color =
                        getColor(
                                R.color.fake
                        );

                break;

            case "ORIGINAL":

                color =
                        getColor(
                                R.color.verified
                        );

                break;

            default:

                color =
                        getColor(
                                R.color.text_dim
                        );

                break;
        }

        tvPrediction.setTextColor(
                color
        );

        // --------------------------------------------------
        // SHOW CONFIDENCE
        // --------------------------------------------------

        tvConfidence.setText(
                getString(
                        R.string.confidence
                )
                        + ": "
                        + confidence
                        + "%"
        );

        // --------------------------------------------------
        // SHOW AI REASONS
        // --------------------------------------------------

        if (reasonsRaw != null
                && !reasonsRaw.trim().isEmpty()) {

            String[] reasons =
                    reasonsRaw.split("\\|");

            for (String reason : reasons) {

                if (reason == null
                        || reason.trim().isEmpty()) {

                    continue;
                }

                TextView tv =
                        new TextView(this);

                tv.setText(
                        "• "
                                + reason.trim()
                );

                tv.setTextColor(
                        getColor(
                                R.color.text_dim
                        )
                );

                tv.setPadding(
                        0,
                        4,
                        0,
                        4
                );

                reasonsContainer.addView(
                        tv
                );
            }
        }

        // --------------------------------------------------
        // SAVE PDF
        // --------------------------------------------------
        //
        // ResultActivity
        //       ↓
        // PdfPreviewActivity
        //       ↓
        // Local PDF generation
        //       ↓
        // Preview
        //       ↓
        // Save to Downloads
        //
        // --------------------------------------------------

        btnDownload.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    ResultActivity.this,
                                    PdfPreviewActivity.class
                            );

                    intent.putExtra(
                            "image_path",
                            imagePath
                    );

                    intent.putExtra(
                            "verdict",
                            verdict
                    );

                    intent.putExtra(
                            "confidence",
                            confidence
                    );

                    intent.putExtra(
                            "reasons",
                            reasonsRaw
                    );

                    startActivity(
                            intent
                    );
                }
        );

        // --------------------------------------------------
        // SCAN AGAIN
        // --------------------------------------------------

        btnScanAgain.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    ResultActivity.this,
                                    ScanActivity.class
                            );

                    startActivity(
                            intent
                    );

                    finish();
                }
        );
    }
}