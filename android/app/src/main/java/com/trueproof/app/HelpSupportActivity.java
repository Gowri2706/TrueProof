package com.trueproof.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HelpSupportActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_help_support);

        // Back button
        TextView btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // FAQ 1
        View faqScan = findViewById(R.id.faqScan);
        TextView answerScan = findViewById(R.id.tvFaqScanAnswer);
        TextView arrowScan = findViewById(R.id.arrowFaqScan);

        // FAQ 2
        View faqFail = findViewById(R.id.faqFail);
        TextView answerFail = findViewById(R.id.tvFaqFailAnswer);
        TextView arrowFail = findViewById(R.id.arrowFaqFail);

        // FAQ 3
        View faqHistory = findViewById(R.id.faqHistory);
        TextView answerHistory = findViewById(R.id.tvFaqHistoryAnswer);
        TextView arrowHistory = findViewById(R.id.arrowFaqHistory);

        // FAQ 4
        View faqDelete = findViewById(R.id.faqDelete);
        TextView answerDelete = findViewById(R.id.tvFaqDeleteAnswer);
        TextView arrowDelete = findViewById(R.id.arrowFaqDelete);

        // FAQ 1 click
        faqScan.setOnClickListener(v -> {
            toggleAnswer(answerScan, arrowScan);
        });

        // FAQ 2 click
        faqFail.setOnClickListener(v -> {
            toggleAnswer(answerFail, arrowFail);
        });

        // FAQ 3 click
        faqHistory.setOnClickListener(v -> {
            toggleAnswer(answerHistory, arrowHistory);
        });

        // FAQ 4 click
        faqDelete.setOnClickListener(v -> {
            toggleAnswer(answerDelete, arrowDelete);
        });
    }

    private void toggleAnswer(TextView answer, TextView arrow) {

        if (answer.getVisibility() == View.VISIBLE) {

            // Hide answer
            answer.setVisibility(View.GONE);

            // Down arrow
            arrow.setText("⌄");

        } else {

            // Show answer
            answer.setVisibility(View.VISIBLE);

            // Up arrow
            arrow.setText("⌃");
        }
    }
}