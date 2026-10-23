
package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "trueproof_prefs";
    private static final String PROFILE_PHOTO = "profile_photo";

    private RecyclerView recyclerRecentScans;

    private TextView tvUserName;
    private ImageView imgProfileLogo;
    private TextView tvFraudAmount;
    private TextView tvFraudCount;

    private SupabaseAuth supabaseAuth;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
                LocaleHelper.onAttach(newBase)
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        supabaseAuth = new SupabaseAuth(this);

        tvUserName = findViewById(R.id.tvUserName);
        imgProfileLogo = findViewById(R.id.imgProfileLogo);
        tvFraudAmount = findViewById(R.id.tvFraudAmount);
        tvFraudCount = findViewById(R.id.tvFraudCount);

        recyclerRecentScans =
                findViewById(R.id.recyclerRecentScans);

        recyclerRecentScans.setLayoutManager(
                new LinearLayoutManager(this)
        );

        loadUserProfile();
        setupQuickActions();
        setupBottomNavigation();
        loadRecentScans();
    }

    @Override
    protected void onResume() {
        super.onResume();

        loadUserProfile();

        if (recyclerRecentScans != null) {
            loadRecentScans();
        }
    }

    // =========================================================
    // USER PROFILE
    // =========================================================

    private void loadUserProfile() {

        String firstName = supabaseAuth.getFirstName();
        String lastName = supabaseAuth.getLastName();

        if (firstName == null) {
            firstName = "";
        }

        if (lastName == null) {
            lastName = "";
        }

        firstName = firstName.trim();
        lastName = lastName.trim();

        String fullName;

        if (!firstName.isEmpty() && !lastName.isEmpty()) {
            fullName = firstName + " " + lastName;
        } else if (!firstName.isEmpty()) {
            fullName = firstName;
        } else if (!lastName.isEmpty()) {
            fullName = lastName;
        } else {
            fullName = "User";
        }

        tvUserName.setText(fullName);

        loadProfilePhoto(firstName, fullName);
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
                prefs.getString(PROFILE_PHOTO, "");

        if (!photoPath.trim().isEmpty()) {

            File photoFile = new File(photoPath);

            if (photoFile.exists()) {

                Bitmap bitmap =
                        BitmapFactory.decodeFile(
                                photoFile.getAbsolutePath()
                        );

                if (bitmap != null) {

                    Bitmap circularBitmap =
                            createCircularBitmap(bitmap);

                    imgProfileLogo.setImageBitmap(circularBitmap);

                    imgProfileLogo.setBackgroundResource(
                            android.R.color.transparent
                    );

                    imgProfileLogo.setScaleType(
                            ImageView.ScaleType.CENTER_CROP
                    );

                    imgProfileLogo.setPadding(0, 0, 0, 0);

                    imgProfileLogo.setContentDescription(
                            "Profile photo"
                    );

                    return;
                }
            }
        }

        // NO PHOTO: SHOW INITIAL

        String initial = "U";

        if (firstName != null
                && !firstName.trim().isEmpty()) {

            initial = firstName.trim()
                    .substring(0, 1)
                    .toUpperCase(Locale.ROOT);

        } else if (fullName != null
                && !fullName.trim().isEmpty()) {

            initial = fullName.trim()
                    .substring(0, 1)
                    .toUpperCase(Locale.ROOT);
        }

        updateProfileLetter(initial);
    }

    // =========================================================
    // CREATE CIRCULAR BITMAP
    // =========================================================

    private Bitmap createCircularBitmap(Bitmap source) {

        int size = Math.min(
                source.getWidth(),
                source.getHeight()
        );

        int left = (source.getWidth() - size) / 2;
        int top = (source.getHeight() - size) / 2;

        Bitmap croppedBitmap = Bitmap.createBitmap(
                source,
                left,
                top,
                size,
                size
        );

        Bitmap output = Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BitmapShader shader = new BitmapShader(
                croppedBitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
        );

        paint.setShader(shader);

        float radius = size / 2f;

        canvas.drawCircle(
                radius,
                radius,
                radius,
                paint
        );

        return output;
    }

    // =========================================================
    // PROFILE INITIAL
    // =========================================================

    private void updateProfileLetter(String initial) {

        Bitmap bitmap = Bitmap.createBitmap(
                96,
                96,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(
                getResources().getColor(R.color.primary)
        );

        paint.setTextSize(42);

        paint.setTypeface(Typeface.DEFAULT_BOLD);

        paint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics metrics = paint.getFontMetrics();

        float x = 48;

        float y = 48 - (
                metrics.ascent + metrics.descent
        ) / 2;

        canvas.drawText(
                initial,
                x,
                y,
                paint
        );

        imgProfileLogo.setImageBitmap(bitmap);

        imgProfileLogo.setBackgroundResource(
                R.drawable.bg_profile_circle
        );

        imgProfileLogo.setScaleType(
                ImageView.ScaleType.CENTER
        );

        imgProfileLogo.setPadding(0, 0, 0, 0);

        imgProfileLogo.setContentDescription(
                "Profile " + initial
        );
    }

    // =========================================================
    // QUICK ACTIONS
    // =========================================================

    private void setupQuickActions() {

        // SCAN SCREENSHOT

        findViewById(R.id.btnUploadScreenshot)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            MainActivity.this,
                            ScanActivity.class
                    );

                    intent.putExtra("mode", "gallery");

                    startActivity(intent);
                });

        // VIEW HISTORY

        findViewById(R.id.btnOpenCamera)
                .setOnClickListener(v -> {

                    Intent intent = new Intent(
                            MainActivity.this,
                            HistoryActivity.class
                    );

                    startActivity(intent);
                });
    }

    // =========================================================
    // BOTTOM NAVIGATION
    // =========================================================

    private void setupBottomNavigation() {

        BottomNavigationView nav =
                findViewById(R.id.bottomNav);

        nav.setSelectedItemId(R.id.nav_home);

        nav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {

                return true;

            } else if (id == R.id.nav_scan) {

                startActivity(
                        new Intent(
                                MainActivity.this,
                                ScanActivity.class
                        )
                );

                return true;

            } else if (id == R.id.nav_history) {

                startActivity(
                        new Intent(
                                MainActivity.this,
                                HistoryActivity.class
                        )
                );

                return true;

            } else if (id == R.id.nav_profile) {

                startActivity(
                        new Intent(
                                MainActivity.this,
                                ProfileActivity.class
                        )
                );

                return true;
            }

            return false;
        });
    }

    // =========================================================
    // LOAD RECENT SCANS FOR CURRENT USER
    // =========================================================

    private void loadRecentScans() {

        DatabaseHelper db = new DatabaseHelper(this);

        List<HistoryItem> userScans = new ArrayList<>();

        try {

            String userId = supabaseAuth.getUserId();

            if (userId != null && !userId.trim().isEmpty()) {

                // IMPORTANT:
                // Fetch scans belonging to the logged-in account only.
                userScans = db.getScansForUser(userId);

                if (userScans == null) {
                    userScans = new ArrayList<>();
                }

            } else {

                // No logged-in user: do not show another account's scans.
                userScans = new ArrayList<>();
            }

            updateFraudSummary(userScans);

            // Show latest 4 scans only.
            List<HistoryItem> recentScans =
                    new ArrayList<>(userScans);

            if (recentScans.size() > 4) {
                recentScans = new ArrayList<>(
                        recentScans.subList(0, 4)
                );
            }

            HistoryAdapter adapter = new HistoryAdapter(
                    recentScans,
                    item -> {

                        Intent intent = new Intent(
                                MainActivity.this,
                                ResultActivity.class
                        );

                        intent.putExtra("scan_id", item.scanId);
                        intent.putExtra("verdict", item.verdict);
                        intent.putExtra("confidence", item.confidence);
                        intent.putExtra("image_path", item.imagePath);

                        intent.putExtra(
                                "reasons",
                                "On-device AI image analysis"
                        );

                        if (item.amount != null) {
                            intent.putExtra("amount", item.amount);
                        }

                        startActivity(intent);
                    }
            );

            recyclerRecentScans.setAdapter(adapter);

        } catch (Exception e) {

            android.util.Log.e(
                    "TRUESHOT_HOME",
                    "Could not load current user's scans",
                    e
            );

            updateFraudSummary(new ArrayList<>());

            recyclerRecentScans.setAdapter(
                    new HistoryAdapter(
                            new ArrayList<>(),
                            item -> {
                                // No scan item to open.
                            }
                    )
            );

        } finally {
            db.close();
        }
    }

    // =========================================================
    // FRAUD SUMMARY
    // =========================================================

    private void updateFraudSummary(List<HistoryItem> scans) {

        double fraudAmount = 0.0;

        int fraudCount = 0;

        Calendar now = Calendar.getInstance();

        int currentMonth = now.get(Calendar.MONTH);
        int currentYear = now.get(Calendar.YEAR);

        for (HistoryItem item : scans) {

            if (item == null) {
                continue;
            }

            // CHECK VERDICT

            String verdict = item.verdict;

            if (verdict == null) {
                continue;
            }

            verdict = verdict.trim();

            boolean isFraud =
                    verdict.equalsIgnoreCase("FAKE")
                            || verdict.equalsIgnoreCase("EDITED");

            if (!isFraud) {
                continue;
            }

            // CHECK SCAN DATE

            Calendar scanDate = Calendar.getInstance();

            scanDate.setTimeInMillis(item.timestamp);

            int scanMonth = scanDate.get(Calendar.MONTH);
            int scanYear = scanDate.get(Calendar.YEAR);

            // Only current month.

            if (scanMonth != currentMonth
                    || scanYear != currentYear) {
                continue;
            }

            // FLAGGED COUNT

            fraudCount++;

            // AMOUNT

            if (item.amount == null) {
                continue;
            }

            String amountText = item.amount.trim();

            if (amountText.isEmpty()) {
                continue;
            }

            amountText = amountText
                    .replace("₹", "")
                    .replace("Rs.", "")
                    .replace("Rs", "")
                    .replace("INR", "")
                    .replace(",", "")
                    .trim();

            if (amountText.equalsIgnoreCase("unknown")
                    || amountText.equalsIgnoreCase("amount unknown")) {
                continue;
            }

            try {

                double amount = Double.parseDouble(amountText);

                if (amount >= 0) {
                    fraudAmount += amount;
                }

            } catch (NumberFormatException ignored) {
                // Ignore invalid amounts.
            }
        }

        // UPDATE UI

        tvFraudAmount.setText(
                formatAmount(fraudAmount)
        );

        tvFraudCount.setText(
                "Across " + fraudCount + " flagged screenshots"
        );
    }

    // =========================================================
    // FORMAT AMOUNT
    // =========================================================

    private String formatAmount(double amount) {

        if (amount == Math.floor(amount)) {

            return String.format(
                    Locale.getDefault(),
                    "₹%.0f",
                    amount
            );
        }

        return String.format(
                Locale.getDefault(),
                "₹%.2f",
                amount
        );
    }
}
