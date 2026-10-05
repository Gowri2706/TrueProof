package com.trueproof.app;

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PrivacyActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_privacy);

        TextView btnBack =
                findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
    }
}