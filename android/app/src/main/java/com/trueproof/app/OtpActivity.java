package com.trueproof.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import okhttp3.Callback;
import okhttp3.Response;

public class OtpActivity extends AppCompatActivity {

    private TextInputEditText etOtp;
    private MaterialButton btnVerifyOtp;

    private SupabaseAuth supabaseAuth;

    private String email;
    private String firstName;
    private String lastName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_otp);

        supabaseAuth = new SupabaseAuth(this);

        etOtp = findViewById(R.id.etOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);

        email = getIntent().getStringExtra("email");
        firstName = getIntent().getStringExtra("first_name");
        lastName = getIntent().getStringExtra("last_name");

        if (email == null) email = "";
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
    }

    private void verifyOtp() {

        String token = "";

        if (etOtp.getText() != null) {
            token = etOtp.getText().toString().trim();
        }

        if (token.isEmpty()) {
            etOtp.setError("Enter OTP");
            etOtp.requestFocus();
            return;
        }

        if (token.length() != 8) {
            etOtp.setError("Enter 8 digit OTP");
            etOtp.requestFocus();
            return;
        }

        btnVerifyOtp.setEnabled(false);

        supabaseAuth.verifyEmailOtp(
                email,
                token,
                new Callback() {

                    @Override
                    public void onFailure(
                            okhttp3.Call call,
                            IOException e
                    ) {

                        runOnUiThread(() -> {

                            btnVerifyOtp.setEnabled(true);

                            Toast.makeText(
                                    OtpActivity.this,
                                    "Verification failed: "
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onResponse(
                            okhttp3.Call call,
                            Response response
                    ) throws IOException {

                        if (!response.isSuccessful()) {

                            runOnUiThread(() -> {

                                btnVerifyOtp.setEnabled(true);

                                Toast.makeText(
                                        OtpActivity.this,
                                        "Invalid OTP: "
                                                + response.code(),
                                        Toast.LENGTH_LONG
                                ).show();
                            });

                            response.close();
                            return;
                        }

                        supabaseAuth.saveProfile(
                                firstName,
                                lastName,
                                email,
                                new Callback() {

                                    @Override
                                    public void onFailure(
                                            okhttp3.Call call,
                                            IOException e
                                    ) {

                                        runOnUiThread(() -> {

                                            btnVerifyOtp.setEnabled(true);

                                            Toast.makeText(
                                                    OtpActivity.this,
                                                    "Profile save failed: "
                                                            + e.getMessage(),
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        });
                                    }

                                    @Override
                                    public void onResponse(
                                            okhttp3.Call call,
                                            Response profileResponse
                                    ) throws IOException {

                                        runOnUiThread(() -> {

                                            if (profileResponse.isSuccessful()) {

                                                Intent intent =
                                                        new Intent(
                                                                OtpActivity.this,
                                                                MainActivity.class
                                                        );

                                                startActivity(intent);
                                                finish();

                                            } else {

                                                Toast.makeText(
                                                        OtpActivity.this,
                                                        "Profile save failed: "
                                                                + profileResponse.code(),
                                                        Toast.LENGTH_LONG
                                                ).show();

                                                btnVerifyOtp.setEnabled(true);
                                            }
                                        });

                                        profileResponse.close();
                                    }
                                }
                        );

                        response.close();
                    }
                }
        );
    }
}