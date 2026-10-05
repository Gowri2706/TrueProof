package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.io.File;

public class ProfileActivity extends AppCompatActivity {

    private static final String PREFS = "trueproof_prefs";
    private static final String PROFILE_PHOTO = "profile_photo";

    private TextView tvProfileInitial;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_profile);

        tvProfileInitial =
                findViewById(R.id.tvProfileInitial);

        TextView tvProfileName =
                findViewById(R.id.tvProfileName);

        TextView tvProfileEmail =
                findViewById(R.id.tvProfileEmail);

        MaterialButton btnLogout =
                findViewById(R.id.btnLogout);

        LinearLayout rowEditProfile =
                findViewById(R.id.rowEditProfile);

        LinearLayout rowLanguage =
                findViewById(R.id.rowLanguage);

        LinearLayout rowHelpSupport =
                findViewById(R.id.rowHelpSupport);

        LinearLayout rowAbout =
                findViewById(R.id.rowAbout);

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String firstName =
                prefs.getString(
                        "first_name",
                        ""
                );

        String lastName =
                prefs.getString(
                        "last_name",
                        ""
                );

        String email =
                prefs.getString(
                        "email",
                        ""
                );

        String fullName =
                (firstName + " " + lastName).trim();

        // Localized fallback name
        if (fullName.isEmpty()) {
            fullName =
                    getString(
                            R.string.trueproof_user
                    );
        }

        // Localized fallback email
        if (email.isEmpty()) {
            email =
                    getString(
                            R.string.no_email_available
                    );
        }

        // Profile name
        tvProfileName.setText(fullName);

        // Profile email
        tvProfileEmail.setText(email);

        // Load profile photo or initial
        loadProfilePhoto(
                firstName,
                fullName
        );

        // -----------------------------------------------------
        // EDIT PROFILE
        // -----------------------------------------------------

        rowEditProfile.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            EditProfileActivity.class
                    );

            startActivity(intent);
        });

        // -----------------------------------------------------
        // LANGUAGE
        // -----------------------------------------------------

        rowLanguage.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            LanguageActivity.class
                    );

            startActivity(intent);
        });

        // -----------------------------------------------------
        // HELP & SUPPORT
        // -----------------------------------------------------

        rowHelpSupport.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            HelpSupportActivity.class
                    );

            startActivity(intent);
        });

        // -----------------------------------------------------
        // ABOUT
        // -----------------------------------------------------

        rowAbout.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            ProfileActivity.this,
                            AboutActivity.class
                    );

            startActivity(intent);
        });

        // -----------------------------------------------------
        // LOGOUT
        // -----------------------------------------------------

        btnLogout.setOnClickListener(v -> logout());

        // -----------------------------------------------------
        // BOTTOM NAVIGATION
        // -----------------------------------------------------

        BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);

        bottomNav.setSelectedItemId(
                R.id.nav_profile
        );

        bottomNav.setOnItemSelectedListener(item -> {

            int id =
                    item.getItemId();

            if (id == R.id.nav_home) {

                startActivity(
                        new Intent(
                                ProfileActivity.this,
                                MainActivity.class
                        )
                );

                finish();

                return true;

            } else if (id == R.id.nav_scan) {

                startActivity(
                        new Intent(
                                ProfileActivity.this,
                                ScanActivity.class
                        )
                );

                finish();

                return true;

            } else if (id == R.id.nav_history) {

                startActivity(
                        new Intent(
                                ProfileActivity.this,
                                HistoryActivity.class
                        )
                );

                finish();

                return true;

            } else if (id == R.id.nav_profile) {

                return true;
            }

            return false;
        });
    }

    // =========================================================
    // ON RESUME
    // =========================================================

    @Override
    protected void onResume() {
        super.onResume();

        loadProfileData();
    }

    // =========================================================
    // LOAD PROFILE DATA
    // =========================================================

    private void loadProfileData() {

        TextView tvProfileName =
                findViewById(R.id.tvProfileName);

        TextView tvProfileEmail =
                findViewById(R.id.tvProfileEmail);

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String firstName =
                prefs.getString(
                        "first_name",
                        ""
                );

        String lastName =
                prefs.getString(
                        "last_name",
                        ""
                );

        String email =
                prefs.getString(
                        "email",
                        ""
                );

        String fullName =
                (firstName + " " + lastName).trim();

        if (fullName.isEmpty()) {

            fullName =
                    getString(
                            R.string.trueproof_user
                    );
        }

        if (email.isEmpty()) {

            email =
                    getString(
                            R.string.no_email_available
                    );
        }

        tvProfileName.setText(fullName);
        tvProfileEmail.setText(email);

        loadProfilePhoto(
                firstName,
                fullName
        );
    }

    // =========================================================
    // LOAD PROFILE PHOTO
    // =========================================================

    private void loadProfilePhoto(
            String firstName,
            String fullName
    ) {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String photoPath =
                prefs.getString(
                        PROFILE_PHOTO,
                        ""
                );

        // -----------------------------------------------------
        // PHOTO EXISTS
        // -----------------------------------------------------

        if (photoPath != null
                && !photoPath.trim().isEmpty()) {

            File photoFile =
                    new File(photoPath);

            if (photoFile.exists()) {

                Bitmap bitmap =
                        BitmapFactory.decodeFile(
                                photoFile.getAbsolutePath()
                        );

                if (bitmap != null) {

                    RoundedBitmapDrawable circularPhoto =
                            RoundedBitmapDrawableFactory.create(
                                    getResources(),
                                    bitmap
                            );

                    circularPhoto.setCircular(true);

                    tvProfileInitial.setBackground(
                            circularPhoto
                    );

                    tvProfileInitial.setText("");

                    tvProfileInitial.setGravity(
                            android.view.Gravity.CENTER
                    );

                    return;
                }
            }
        }

        // -----------------------------------------------------
        // NO PHOTO → SHOW INITIAL
        // -----------------------------------------------------

        showProfileInitial(
                firstName,
                fullName
        );
    }

    // =========================================================
    // SHOW PROFILE INITIAL
    // =========================================================

    private void showProfileInitial(
            String firstName,
            String fullName
    ) {

        String initial;

        if (firstName != null
                && !firstName.trim().isEmpty()) {

            initial =
                    firstName
                            .trim()
                            .substring(0, 1)
                            .toUpperCase();

        } else if (fullName != null
                && !fullName.trim().isEmpty()) {

            initial =
                    fullName
                            .trim()
                            .substring(0, 1)
                            .toUpperCase();

        } else {

            initial = "T";
        }

        tvProfileInitial.setBackgroundResource(
                R.drawable.tp_bg_avatar
        );

        tvProfileInitial.setText(
                initial
        );

        tvProfileInitial.setTextColor(
                getResources().getColor(
                        R.color.white
                )
        );

        tvProfileInitial.setTextSize(24);

        tvProfileInitial.setGravity(
                android.view.Gravity.CENTER
        );
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    private void logout() {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        prefs.edit()
                .clear()
                .apply();

        Intent intent =
                new Intent(
                        ProfileActivity.this,
                        LoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);

        finish();
    }
}