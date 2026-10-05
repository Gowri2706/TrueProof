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

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_history);

        RecyclerView recyclerView = findViewById(R.id.recyclerHistory);
        EditText etSearch = findViewById(R.id.etSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DatabaseHelper db = new DatabaseHelper(this);
        allItems = db.getAllScans();

        adapter = new HistoryAdapter(
                new ArrayList<>(allItems),
                item -> Toast.makeText(
                        HistoryActivity.this,
                        "Scan ID: " + item.scanId,
                        Toast.LENGTH_SHORT
                ).show()
        );

        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setupBottomNavigation();
    }

    private void setupBottomNavigation() {

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setSelectedItemId(R.id.nav_history);

        bottomNav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {

                startActivity(
                        new Intent(
                                HistoryActivity.this,
                                MainActivity.class
                        )
                );

                finish();
                return true;

            } else if (id == R.id.nav_scan) {

                startActivity(
                        new Intent(
                                HistoryActivity.this,
                                ScanActivity.class
                        )
                );

                finish();
                return true;

            } else if (id == R.id.nav_history) {

                return true;

            } else if (id == R.id.nav_profile) {

                startActivity(
                        new Intent(
                                HistoryActivity.this,
                                ProfileActivity.class
                        )
                );

                finish();
                return true;
            }

            return false;
        });
    }

    private void filter(String query) {

        List<HistoryItem> filtered = new ArrayList<>();

        String q = query.toLowerCase().trim();

        for (HistoryItem item : allItems) {

            boolean matches =
                    q.isEmpty()
                            || (
                            item.upiId != null
                                    && item.upiId
                                    .toLowerCase()
                                    .contains(q)
                    )
                            || (
                            item.scanId != null
                                    && item.scanId
                                    .toLowerCase()
                                    .contains(q)
                    );

            if (matches) {
                filtered.add(item);
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

        RecyclerView recyclerView =
                findViewById(R.id.recyclerHistory);

        recyclerView.setAdapter(adapter);
    }
}