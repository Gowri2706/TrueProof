package com.trueproof.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class EditProfileActivity extends AppCompatActivity {

    private static final String PREFS = "trueproof_prefs";
    private static final String PROFILE_PHOTO = "profile_photo";

    private ImageView profileImage;

    // Gallery
    private final ActivityResultLauncher<Intent>
            galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode()
                                == RESULT_OK &&
                                result.getData() != null) {

                            Uri uri =
                                    result.getData().getData();

                            if (uri != null) {
                                saveGalleryPhoto(uri);
                            }
                        }
                    }
            );

    // Camera
    private final ActivityResultLauncher<Void>
            cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicturePreview(),
                    bitmap -> {

                        if (bitmap != null) {
                            saveCameraPhoto(bitmap);
                        }
                    }
            );

    // Camera permission
    private final ActivityResultLauncher<String>
            cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {

                        if (granted) {
                            cameraLauncher.launch(null);
                        } else {
                            Toast.makeText(
                                    this,
                                    "Camera permission is required",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_edit_profile);

        // Back arrow
        TextView btnBack =
                findViewById(R.id.btnBack);

        // Profile image
        profileImage =
                findViewById(R.id.tvEditProfileInitial);

        // Change photo
        TextView tvChangePhoto =
                findViewById(R.id.tvChangePhoto);

        // Input fields
        TextInputEditText etFullName =
                findViewById(R.id.etFullName);

        TextInputEditText etEmail =
                findViewById(R.id.etEmail);

        TextInputEditText etPhone =
                findViewById(R.id.etPhone);

        // Save button
        MaterialButton btnSaveProfile =
                findViewById(R.id.btnSaveProfile);

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS,
                        MODE_PRIVATE
                );

        String firstName =
                prefs.getString("first_name", "");

        String lastName =
                prefs.getString("last_name", "");

        String email =
                prefs.getString("email", "");

        String phone =
                prefs.getString("phone", "");

        String fullName =
                (firstName + " " + lastName).trim();

        // Existing profile data
        etFullName.setText(fullName);
        etEmail.setText(email);
        etPhone.setText(phone);

        // Existing photo
        loadSavedPhoto();

        // Back
        btnBack.setOnClickListener(v -> finish());

        // Change photo
        tvChangePhoto.setOnClickListener(
                v -> showPhotoChooser()
        );

        // Save profile
        btnSaveProfile.setOnClickListener(v -> {

            String newFullName =
                    etFullName.getText()
                            .toString()
                            .trim();

            String newEmail =
                    etEmail.getText()
                            .toString()
                            .trim();

            String newPhone =
                    etPhone.getText()
                            .toString()
                            .trim();

            if (newFullName.isEmpty()) {

                etFullName.setError(
                        "Please enter your full name"
                );

                etFullName.requestFocus();

                return;
            }

            String newFirstName;
            String newLastName;

            String[] nameParts =
                    newFullName.split("\\s+", 2);

            newFirstName =
                    nameParts[0];

            if (nameParts.length > 1) {
                newLastName = nameParts[1];
            } else {
                newLastName = "";
            }

            prefs.edit()
                    .putString(
                            "first_name",
                            newFirstName
                    )
                    .putString(
                            "last_name",
                            newLastName
                    )
                    .putString(
                            "email",
                            newEmail
                    )
                    .putString(
                            "phone",
                            newPhone
                    )
                    .apply();

            Toast.makeText(
                    EditProfileActivity.this,
                    "Profile updated successfully",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
        });
    }

    private void showPhotoChooser() {

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Choose profile photo")
                .setItems(
                        new String[]{
                                "Choose from gallery",
                                "Take a photo"
                        },
                        (dialog, which) -> {

                            if (which == 0) {
                                openGallery();
                            } else {
                                openCamera();
                            }
                        }
                )
                .show();
    }

    private void openGallery() {

        Intent intent =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        intent.setType("image/*");

        galleryLauncher.launch(intent);
    }

    private void openCamera() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            cameraLauncher.launch(null);

        } else {

            cameraPermissionLauncher.launch(
                    Manifest.permission.CAMERA
            );
        }
    }

    private void saveGalleryPhoto(Uri uri) {

        try {

            File photoFile =
                    new File(
                            getFilesDir(),
                            "profile_photo.jpg"
                    );

            InputStream inputStream =
                    getContentResolver()
                            .openInputStream(uri);

            FileOutputStream outputStream =
                    new FileOutputStream(photoFile);

            byte[] buffer =
                    new byte[8192];

            int length;

            while ((length =
                    inputStream.read(buffer)) > 0) {

                outputStream.write(
                        buffer,
                        0,
                        length
                );
            }

            outputStream.close();
            inputStream.close();

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .edit()
                    .putString(
                            PROFILE_PHOTO,
                            photoFile.getAbsolutePath()
                    )
                    .apply();

            loadSavedPhoto();

            Toast.makeText(
                    this,
                    "Photo selected",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to select photo",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void saveCameraPhoto(Bitmap bitmap) {

        try {

            File photoFile =
                    new File(
                            getFilesDir(),
                            "profile_photo.jpg"
                    );

            FileOutputStream outputStream =
                    new FileOutputStream(photoFile);

            bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    outputStream
            );

            outputStream.flush();
            outputStream.close();

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            )
                    .edit()
                    .putString(
                            PROFILE_PHOTO,
                            photoFile.getAbsolutePath()
                    )
                    .apply();

            loadSavedPhoto();

            Toast.makeText(
                    this,
                    "Photo captured",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to save photo",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void loadSavedPhoto() {

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

        if (photoPath == null ||
                photoPath.isEmpty()) {
            return;
        }

        File photoFile =
                new File(photoPath);

        if (!photoFile.exists()) {
            return;
        }

        Bitmap bitmap =
                BitmapFactory.decodeFile(
                        photoFile.getAbsolutePath()
                );

        if (bitmap != null) {

            profileImage.setImageBitmap(bitmap);

            profileImage.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            profileImage.setPadding(
                    0,
                    0,
                    0,
                    0
            );
        }
    }
}