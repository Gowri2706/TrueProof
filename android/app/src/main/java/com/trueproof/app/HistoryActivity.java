
package com.trueproof.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private List<HistoryItem> allItems;
    private HistoryAdapter adapter;
    private DatabaseHelper db;
    private SupabaseAuth supabaseAuth;
    private String currentUserId = "";
    private RecyclerView recyclerView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerHistory);
        EditText etSearch = findViewById(R.id.etSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = new DatabaseHelper(this);
        supabaseAuth = new SupabaseAuth(this);

        // Get the currently logged-in Supabase user ID.
        currentUserId = supabaseAuth.getUserId();

        allItems = new ArrayList<>();

        adapter = new HistoryAdapter(
                new ArrayList<>(),
                item -> Toast.makeText(
                        HistoryActivity.this,
                        "Scan ID: " + item.scanId,
                        Toast.LENGTH_SHORT
                ).show()
        );

        recyclerView.setAdapter(adapter);

        if (currentUserId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please login to view your scan history",
                    Toast.LENGTH_LONG
            ).show();
        } else {
            loadUserHistory();
        }

        etSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setupBottomNavigation();
    }

    private void loadUserHistory() {
        currentUserId = supabaseAuth.getUserId();

        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            allItems = new ArrayList<>();
        } else {
            // Only load scans belonging to this user.
            allItems = db.getScansForUser(currentUserId);
        }

        filter("");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (db != null && supabaseAuth != null) {
            loadUserHistory();
        }
    }

    private void setupBottomNavigation() {

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_history);

        bottomNav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;

            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, ScanActivity.class));
                finish();
                return true;

            } else if (id == R.id.nav_history) {
                return true;

            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            }

            return false;
        });
    }

    private void filter(String query) {

        List<HistoryItem> filtered = new ArrayList<>();
        String q = query.toLowerCase().trim();

        if (allItems != null) {
            for (HistoryItem item : allItems) {

                boolean matches =
                        q.isEmpty()
                                || (item.upiId != null
                                && item.upiId.toLowerCase().contains(q))
                                || (item.scanId != null
                                && item.scanId.toLowerCase().contains(q));

                if (matches) {
                    filtered.add(item);
                }
            }
        }

        adapter = new HistoryAdapter(
                filtered,
                item -> Toast.makeText(
                        HistoryActivity.this,
                        "Scan ID: " + item.scanId,
                        Toast.LENGTH_SHORT
                ).show()
        );

        recyclerView.setAdapter(adapter);
    }
}
