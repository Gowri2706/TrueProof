package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LanguageActivity extends AppCompatActivity {

    private static final String PREFS = "trueproof_prefs";
    private static final String KEY_LANGUAGE = "app_language";

    private RadioButton radioEnglish;
    private RadioButton radioTamil;
    private RadioButton radioHindi;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_language);

        TextView btnBack = findViewById(R.id.btnBack);

        LinearLayout rowEnglish = findViewById(R.id.rowEnglish);
        LinearLayout rowTamil = findViewById(R.id.rowTamil);
        LinearLayout rowHindi = findViewById(R.id.rowHindi);

        radioEnglish = findViewById(R.id.radioEnglish);
        radioTamil = findViewById(R.id.radioTamil);
        radioHindi = findViewById(R.id.radioHindi);

        String language =
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString(KEY_LANGUAGE, "en");

        updateSelection(language);

        btnBack.setOnClickListener(v -> finish());

        rowEnglish.setOnClickListener(v -> changeLanguage("en"));

        rowTamil.setOnClickListener(v -> changeLanguage("ta"));

        rowHindi.setOnClickListener(v -> changeLanguage("hi"));
    }

    private void changeLanguage(String language) {

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, language)
                .apply();

        LocaleHelper.setLocale(this, language);

        Intent intent =
                new Intent(
                        LanguageActivity.this,
                        MainActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }

    private void updateSelection(String language) {

        radioEnglish.setChecked(
                "en".equals(language)
        );

        radioTamil.setChecked(
                "ta".equals(language)
        );

        radioHindi.setChecked(
                "hi".equals(language)
        );
    }
}