package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ScanActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private Button btnAnalyze;
    private ProgressBar progressBar;
    private TextView tvStatus;

    private Uri selectedImageUri;
    private File selectedImageFile;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // ---------------------------------------------------------
    // GALLERY
    // ---------------------------------------------------------

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {

                        if (uri != null) {

                            selectedImageUri = uri;

                            imgPreview.setImageURI(uri);

                            selectedImageFile =
                                    copyUriToFile(uri);

                            btnAnalyze.setEnabled(
                                    selectedImageFile != null
                            );

                            if (selectedImageFile != null) {

                                tvStatus.setVisibility(
                                        View.VISIBLE
                                );

                                tvStatus.setText(
                                        "Screenshot selected"
                                );
                            }
                        }
                    }
            );

    // ---------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------

    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicturePreview(),
                    bitmap -> {

                        if (bitmap != null) {

                            imgPreview.setImageBitmap(bitmap);

                            selectedImageFile =
                                    bitmapToFile(bitmap);

                            btnAnalyze.setEnabled(
                                    selectedImageFile != null
                            );

                            if (selectedImageFile != null) {

                                tvStatus.setVisibility(
                                        View.VISIBLE
                                );

                                tvStatus.setText(
                                        "Photo captured"
                                );
                            }
                        }
                    }
            );

    // ---------------------------------------------------------
    // ON CREATE
    // ---------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan);

        imgPreview =
                findViewById(R.id.imgPreview);

        btnAnalyze =
                findViewById(R.id.btnAnalyze);

        progressBar =
                findViewById(R.id.progressBar);

        tvStatus =
                findViewById(R.id.tvStatus);

        Button btnGallery =
                findViewById(R.id.btnGallery);

        Button btnCamera =
                findViewById(R.id.btnCamera);

        // Initial state
        btnAnalyze.setEnabled(false);

        progressBar.setVisibility(
                View.GONE
        );

        tvStatus.setVisibility(
                View.GONE
        );

        // -----------------------------------------------------
        // GALLERY BUTTON
        // -----------------------------------------------------

        btnGallery.setOnClickListener(
                v -> galleryLauncher.launch("image/*")
        );

        // -----------------------------------------------------
        // CAMERA BUTTON
        // -----------------------------------------------------

        btnCamera.setOnClickListener(
                v -> cameraLauncher.launch(null)
        );

        // -----------------------------------------------------
        // ANALYZE BUTTON
        // -----------------------------------------------------

        btnAnalyze.setOnClickListener(
                v -> openAnalyzingScreen()
        );

        // -----------------------------------------------------
        // BOTTOM NAVIGATION
        // -----------------------------------------------------

        setupBottomNavigation();

        // -----------------------------------------------------
        // AUTO OPEN MODE
        // -----------------------------------------------------

        String mode =
                getIntent().getStringExtra("mode");

        if ("gallery".equals(mode)) {

            galleryLauncher.launch("image/*");

        } else if ("camera".equals(mode)) {

            cameraLauncher.launch(null);
        }
    }

    // ---------------------------------------------------------
    // OPEN ANALYZING SCREEN
    // ---------------------------------------------------------

    private void openAnalyzingScreen() {

        if (selectedImageFile == null) {

            Toast.makeText(
                    this,
                    "Please select an image first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!selectedImageFile.exists()) {

            Toast.makeText(
                    this,
                    "Selected image is no longer available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Disable button so user cannot double-tap
        btnAnalyze.setEnabled(false);

        // Hide old scan-page loading UI
        progressBar.setVisibility(
                View.GONE
        );

        tvStatus.setVisibility(
                View.GONE
        );

        // -----------------------------------------------------
        // SEND IMAGE PATH TO ANALYZING ACTIVITY
        // -----------------------------------------------------

        Intent intent =
                new Intent(
                        ScanActivity.this,
                        AnalyzingActivity.class
                );

        intent.putExtra(
                "image_path",
                selectedImageFile.getAbsolutePath()
        );

        startActivity(intent);

        // Don't finish ScanActivity yet.
        // AnalyzingActivity will open ResultActivity
        // after the actual analysis is completed.
    }

    // ---------------------------------------------------------
    // COPY GALLERY IMAGE TO CACHE
    // ---------------------------------------------------------

    private File copyUriToFile(Uri uri) {

        try {

            InputStream in =
                    getContentResolver()
                            .openInputStream(uri);

            if (in == null) {
                return null;
            }

            File out =
                    new File(
                            getCacheDir(),
                            "scan_"
                                    + System.currentTimeMillis()
                                    + ".jpg"
                    );

            FileOutputStream fos =
                    new FileOutputStream(out);

            byte[] buffer =
                    new byte[8192];

            int len;

            while ((len = in.read(buffer)) > 0) {

                fos.write(
                        buffer,
                        0,
                        len
                );
            }

            fos.flush();

            fos.close();

            in.close();

            return out;

        } catch (IOException e) {

            Toast.makeText(
                    this,
                    "Could not read image",
                    Toast.LENGTH_SHORT
            ).show();

            return null;
        }
    }

    // ---------------------------------------------------------
    // SAVE CAMERA BITMAP TO CACHE
    // ---------------------------------------------------------

    private File bitmapToFile(Bitmap bitmap) {

        try {

            File out =
                    new File(
                            getCacheDir(),
                            "camera_"
                                    + System.currentTimeMillis()
                                    + ".jpg"
                    );

            FileOutputStream fos =
                    new FileOutputStream(out);

            bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    fos
            );

            fos.flush();

            fos.close();

            return out;

        } catch (IOException e) {

            Toast.makeText(
                    this,
                    "Could not save camera image",
                    Toast.LENGTH_SHORT
            ).show();

            return null;
        }
    }

    // ---------------------------------------------------------
    // BOTTOM NAVIGATION
    // ---------------------------------------------------------

    private void setupBottomNavigation() {

        BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);

        bottomNav.setSelectedItemId(
                R.id.nav_scan
        );

        bottomNav.setOnItemSelectedListener(
                item -> {

                    int id =
                            item.getItemId();

                    // HOME
                    if (id == R.id.nav_home) {

                        startActivity(
                                new Intent(
                                        ScanActivity.this,
                                        MainActivity.class
                                )
                        );

                        finish();

                        return true;
                    }

                    // SCAN
                    else if (id == R.id.nav_scan) {

                        return true;
                    }

                    // HISTORY
                    else if (id == R.id.nav_history) {

                        startActivity(
                                new Intent(
                                        ScanActivity.this,
                                        HistoryActivity.class
                                )
                        );

                        finish();

                        return true;
                    }

                    // PROFILE
                    else if (id == R.id.nav_profile) {

                        startActivity(
                                new Intent(
                                        ScanActivity.this,
                                        ProfileActivity.class
                                )
                        );

                        finish();

                        return true;
                    }

                    return false;
                }
        );
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}