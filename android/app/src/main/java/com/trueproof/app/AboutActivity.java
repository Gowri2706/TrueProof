package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        TextView btnBack =
                findViewById(R.id.btnBack);

        LinearLayout rowTerms =
                findViewById(R.id.rowTerms);

        LinearLayout rowPrivacy =
                findViewById(R.id.rowPrivacy);

        // Back
        btnBack.setOnClickListener(v -> finish());

        // Terms of Service
        rowTerms.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            AboutActivity.this,
                            TermsActivity.class
                    );

            startActivity(intent);
        });

        // Privacy Policy
        rowPrivacy.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            AboutActivity.this,
                            PrivacyActivity.class
                    );

            startActivity(intent);
        });
    }
}