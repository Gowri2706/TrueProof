package com.trueproof.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;

import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etFirstName;
    private TextInputEditText etLastName;
    private TextInputEditText etEmail;

    private MaterialButton btnContinue;
    private MaterialButton btnGoogle;

    private SupabaseAuth supabaseAuth;

    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getData() == null) {
                            enableButtons();
                            return;
                        }

                        try {

                            GoogleSignInAccount account =
                                    GoogleSignIn.getSignedInAccountFromIntent(
                                            result.getData()
                                    ).getResult(ApiException.class);

                            if (account == null) {
                                enableButtons();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Google sign-in failed",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            String idToken = account.getIdToken();

                            if (idToken == null || idToken.isEmpty()) {
                                enableButtons();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Google ID token not received",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            signInToSupabase(
                                    account,
                                    idToken
                            );

                        } catch (ApiException e) {

                            enableButtons();

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Google sign-in failed: "
                                            + e.getStatusCode(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create Supabase auth manager first
        supabaseAuth = new SupabaseAuth(this);

        // If user is already logged in,
        // skip Login screen and go directly to Home.
        if (supabaseAuth.isLoggedIn()) {
            openHome();
            return;
        }

        setContentView(R.layout.activity_login);

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);

        btnContinue = findViewById(R.id.btnContinue);
        btnGoogle = findViewById(R.id.btnGoogle);

        setupGoogleSignIn();

        btnContinue.setOnClickListener(v ->
                continueWithEmail()
        );

        btnGoogle.setOnClickListener(v ->
                continueWithGoogle()
        );
    }

    // =========================================================
    // GOOGLE SIGN-IN SETUP
    // =========================================================

    private void setupGoogleSignIn() {

        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(
                        GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                        .requestIdToken(
                                getString(
                                        R.string.google_web_client_id
                                )
                        )
                        .requestEmail()
                        .build();

        googleSignInClient =
                GoogleSignIn.getClient(
                        this,
                        gso
                );
    }

    // =========================================================
    // CONTINUE WITH GOOGLE
    // =========================================================

    private void continueWithGoogle() {

        btnGoogle.setEnabled(false);
        btnContinue.setEnabled(false);

        // Sign out the previous Google session first.
        // This allows Google to show the account chooser.
        googleSignInClient.signOut()
                .addOnCompleteListener(task -> {

                    Intent signInIntent =
                            googleSignInClient.getSignInIntent();

                    googleSignInLauncher.launch(
                            signInIntent
                    );
                });
    }

    // =========================================================
    // GOOGLE ACCOUNT → SUPABASE
    // =========================================================

    private void signInToSupabase(
            GoogleSignInAccount account,
            String idToken
    ) {

        supabaseAuth.signInWithGoogleIdToken(
                idToken,
                new Callback() {

                    @Override
                    public void onFailure(
                            okhttp3.Call call,
                            IOException e
                    ) {

                        runOnUiThread(() -> {

                            enableButtons();

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Supabase Google login failed: "
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

                            int code = response.code();

                            response.close();

                            runOnUiThread(() -> {

                                enableButtons();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Google login failed: "
                                                + code,
                                        Toast.LENGTH_LONG
                                ).show();
                            });

                            return;
                        }

                        String firstName =
                                account.getGivenName();

                        String lastName =
                                account.getFamilyName();

                        String email =
                                account.getEmail();

                        if (firstName == null) {
                            firstName = "";
                        }

                        if (lastName == null) {
                            lastName = "";
                        }

                        if (email == null) {
                            email = "";
                        }

                        String finalFirstName = firstName;
                        String finalLastName = lastName;
                        String finalEmail = email;

                        response.close();

                        saveGoogleProfile(
                                finalFirstName,
                                finalLastName,
                                finalEmail
                        );
                    }
                }
        );
    }

    // =========================================================
    // SAVE GOOGLE USER PROFILE
    // =========================================================

    private void saveGoogleProfile(
            String firstName,
            String lastName,
            String email
    ) {

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

                            enableButtons();

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Profile save failed: "
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

                        boolean success =
                                response.isSuccessful();

                        response.close();

                        runOnUiThread(() -> {

                            if (success) {

                                openHome();

                            } else {

                                enableButtons();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "Profile save failed",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }
                }
        );
    }

    // =========================================================
    // EMAIL OTP
    // =========================================================

    private void continueWithEmail() {

        String firstName =
                getText(etFirstName);

        String lastName =
                getText(etLastName);

        String email =
                getText(etEmail);

        if (firstName.isEmpty()) {

            etFirstName.setError(
                    "Enter your first name"
            );

            etFirstName.requestFocus();

            return;
        }

        if (lastName.isEmpty()) {

            etLastName.setError(
                    "Enter your last name"
            );

            etLastName.requestFocus();

            return;
        }

        if (email.isEmpty()) {

            etEmail.setError(
                    "Enter your email"
            );

            etEmail.requestFocus();

            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(email)
                .matches()) {

            etEmail.setError(
                    "Enter a valid email"
            );

            etEmail.requestFocus();

            return;
        }

        btnContinue.setEnabled(false);
        btnGoogle.setEnabled(false);

        supabaseAuth.sendEmailOtp(
                email,
                new Callback() {

                    @Override
                    public void onFailure(
                            okhttp3.Call call,
                            IOException e
                    ) {

                        runOnUiThread(() -> {

                            enableButtons();

                            Toast.makeText(
                                    LoginActivity.this,
                                    "Failed to send OTP: "
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

                        boolean success =
                                response.isSuccessful();

                        int code =
                                response.code();

                        response.close();

                        runOnUiThread(() -> {

                            if (success) {

                                Intent intent =
                                        new Intent(
                                                LoginActivity.this,
                                                OtpActivity.class
                                        );

                                intent.putExtra(
                                        "email",
                                        email
                                );

                                intent.putExtra(
                                        "first_name",
                                        firstName
                                );

                                intent.putExtra(
                                        "last_name",
                                        lastName
                                );

                                startActivity(intent);

                                enableButtons();

                            } else {

                                enableButtons();

                                Toast.makeText(
                                        LoginActivity.this,
                                        "OTP send failed: "
                                                + code,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
                    }
                }
        );
    }

    // =========================================================
    // OPEN HOME
    // =========================================================

    private void openHome() {

        Intent intent =
                new Intent(
                        LoginActivity.this,
                        MainActivity.class
                );

        startActivity(intent);

        finish();
    }

    // =========================================================
    // ENABLE BUTTONS
    // =========================================================

    private void enableButtons() {

        if (btnContinue != null) {
            btnContinue.setEnabled(true);
        }

        if (btnGoogle != null) {
            btnGoogle.setEnabled(true);
        }
    }

    // =========================================================
    // GET TEXT
    // =========================================================

    private String getText(
            TextInputEditText editText
    ) {

        if (editText.getText() == null) {
            return "";
        }

        return editText.getText()
                .toString()
                .trim();
    }
}