
package com.trueproof.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
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
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etFirstName;
    private TextInputEditText etLastName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;

    private TextInputLayout tilFirstName;
    private TextInputLayout tilLastName;
    private TextInputLayout tilConfirmPassword;

    private MaterialButton btnContinue;
    private MaterialButton btnGoogle;

    private TextView tvModeTitle;
    private TextView tvModeSubtitle;
    private TextView tvSwitchMode;

    private SupabaseAuth supabaseAuth;
    private GoogleSignInClient googleSignInClient;

    // Start in Sign Up mode
    private boolean isSignUpMode = true;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getData() == null) {
                            enableButtons();
                            showMessage("Google sign-in was cancelled.");
                            return;
                        }

                        try {
                            GoogleSignInAccount account =
                                    GoogleSignIn.getSignedInAccountFromIntent(
                                            result.getData()
                                    ).getResult(ApiException.class);

                            if (account == null ||
                                    account.getIdToken() == null ||
                                    account.getIdToken().isEmpty()) {

                                enableButtons();
                                showMessage("Google ID token not received.");
                                return;
                            }

                            signInToSupabase(account, account.getIdToken());

                        } catch (ApiException e) {
                            enableButtons();
                            showMessage(
                                    "Google sign-in failed. Code: " +
                                            e.getStatusCode()
                            );
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supabaseAuth = new SupabaseAuth(this);

        if (supabaseAuth.isLoggedIn()) {
            openHome();
            return;
        }

        setContentView(R.layout.activity_login);

        // Input fields
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        // Parent layouts: hide the complete field, including its hint
        tilFirstName = findViewById(R.id.tilFirstName);
        tilLastName = findViewById(R.id.tilLastName);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        // Buttons
        btnContinue = findViewById(R.id.btnContinue);
        btnGoogle = findViewById(R.id.btnGoogle);

        // Text views
        tvModeTitle = findViewById(R.id.tvModeTitle);
        tvModeSubtitle = findViewById(R.id.tvModeSubtitle);
        tvSwitchMode = findViewById(R.id.tvSwitchMode);

        setupGoogleSignIn();
        updateModeUI();

        btnContinue.setOnClickListener(v -> submitEmailForm());

        btnGoogle.setOnClickListener(v -> continueWithGoogle());

        tvSwitchMode.setOnClickListener(v -> {
            isSignUpMode = !isSignUpMode;
            clearFieldErrors();
            updateModeUI();
        });
    }

    // =========================================================
    // SWITCH SIGN UP / LOGIN UI
    // =========================================================

    private void updateModeUI() {

        if (isSignUpMode) {

            tvModeTitle.setText("Create your account");
            tvModeSubtitle.setText(
                    "Enter your details to create an account"
            );

            btnContinue.setText("Sign Up");
            tvSwitchMode.setText("Already have an account? Log In");

            tilFirstName.setVisibility(View.VISIBLE);
            tilLastName.setVisibility(View.VISIBLE);
            tilConfirmPassword.setVisibility(View.VISIBLE);

        } else {

            tvModeTitle.setText("Welcome Back");
            tvModeSubtitle.setText(
                    "Log in to your True Shot AI account"
            );

            btnContinue.setText("Log In");
            tvSwitchMode.setText("Don't have an account? Sign Up");

            tilFirstName.setVisibility(View.GONE);
            tilLastName.setVisibility(View.GONE);
            tilConfirmPassword.setVisibility(View.GONE);
        }
    }

    private void clearFieldErrors() {
        etFirstName.setError(null);
        etLastName.setError(null);
        etEmail.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);
    }

    // =========================================================
    // VALIDATE AND SUBMIT EMAIL FORM
    // =========================================================

    private void submitEmailForm() {

        clearFieldErrors();

        String email = getText(etEmail);
        String password = getText(etPassword);
        String firstName = getText(etFirstName);
        String lastName = getText(etLastName);
        String confirmPassword = getText(etConfirmPassword);

        if (isSignUpMode) {

            if (firstName.isEmpty()) {
                etFirstName.setError("Enter your first name");
                etFirstName.requestFocus();
                return;
            }

            if (lastName.isEmpty()) {
                etLastName.setError("Enter your last name");
                etLastName.requestFocus();
                return;
            }
        }

        if (email.isEmpty() ||
                !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (password.length() < 8) {
            etPassword.setError(
                    "Password must be at least 8 characters"
            );
            etPassword.requestFocus();
            return;
        }

        if (isSignUpMode && !password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        setButtonsEnabled(false);

        if (isSignUpMode) {
            performSignUp(firstName, lastName, email, password);
        } else {
            performLogin(email, password);
        }
    }

    // =========================================================
    // SIGN UP
    // =========================================================

    private void performSignUp(
            String firstName,
            String lastName,
            String email,
            String password
    ) {

        supabaseAuth.signUpWithEmailPassword(
                firstName,
                lastName,
                email,
                password,
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException e
                    ) {
                        runOnUiThread(() -> {
                            enableButtons();
                            showMessage(
                                    "Sign up failed: " + e.getMessage()
                            );
                        });
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) throws IOException {

                        boolean success = response.isSuccessful();
                        String responseText = "";

                        if (response.body() != null) {
                            responseText = response.body().string();
                        }

                        String errorMessage =
                                getServerMessage(responseText);

                        response.close();

                        if (!success) {
                            runOnUiThread(() -> {
                                enableButtons();
                                showMessage(
                                        "Sign up failed: " + errorMessage
                                );
                            });
                            return;
                        }

                        runOnUiThread(() -> {

                            if (!supabaseAuth.isLoggedIn()) {
                                enableButtons();

                                showMessage(
                                        "Account created. Email confirmation " +
                                                "may be required. Check your email " +
                                                "and Supabase Auth settings."
                                );
                                return;
                            }

                            saveUserProfile(
                                    firstName,
                                    lastName,
                                    email,
                                    "Account created successfully"
                            );
                        });
                    }
                }
        );
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private void performLogin(String email, String password) {

        supabaseAuth.loginWithEmailPassword(
                email,
                password,
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException e
                    ) {
                        runOnUiThread(() -> {
                            enableButtons();
                            showMessage(
                                    "Login failed: " + e.getMessage()
                            );
                        });
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) throws IOException {

                        boolean success = response.isSuccessful();
                        String responseText = "";

                        if (response.body() != null) {
                            responseText = response.body().string();
                        }

                        String errorMessage =
                                getServerMessage(responseText);

                        response.close();

                        if (!success) {
                            runOnUiThread(() -> {
                                enableButtons();
                                showMessage(
                                        "Login failed: " + errorMessage
                                );
                            });
                            return;
                        }

                        runOnUiThread(() -> {

                            String firstName =
                                    supabaseAuth.getFirstName();

                            String lastName =
                                    supabaseAuth.getLastName();

                            saveUserProfile(
                                    firstName,
                                    lastName,
                                    email,
                                    "Login successful"
                            );
                        });
                    }
                }
        );
    }

    // =========================================================
    // SAVE PROFILE AND OPEN HOME
    // =========================================================

    private void saveUserProfile(
            String firstName,
            String lastName,
            String email,
            String successMessage
    ) {

        supabaseAuth.saveProfile(
                firstName,
                lastName,
                email,
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException e
                    ) {
                        runOnUiThread(() -> {
                            enableButtons();
                            showMessage(
                                    "Profile save failed: " + e.getMessage()
                            );
                        });
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) throws IOException {

                        boolean success = response.isSuccessful();
                        int code = response.code();
                        String responseText = "";

                        if (response.body() != null) {
                            responseText = response.body().string();
                        }

                        String errorMessage =
                                getServerMessage(responseText);

                        response.close();

                        runOnUiThread(() -> {
                            if (success) {
                                showMessage(successMessage);
                                openHome();
                            } else {
                                enableButtons();
                                showMessage(
                                        "Profile save failed (" + code +
                                                "): " + errorMessage
                                );
                            }
                        });
                    }
                }
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
                                getString(R.string.google_web_client_id)
                        )
                        .requestEmail()
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void continueWithGoogle() {

        setButtonsEnabled(false);

        googleSignInClient.signOut()
                .addOnCompleteListener(task -> {
                    googleSignInLauncher.launch(
                            googleSignInClient.getSignInIntent()
                    );
                });
    }

    private void signInToSupabase(
            GoogleSignInAccount account,
            String idToken
    ) {

        supabaseAuth.signInWithGoogleIdToken(
                idToken,
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException e
                    ) {
                        runOnUiThread(() -> {
                            enableButtons();
                            showMessage(
                                    "Google login failed: " + e.getMessage()
                            );
                        });
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response
                    ) throws IOException {

                        boolean success = response.isSuccessful();
                        int code = response.code();
                        String responseText = "";

                        if (response.body() != null) {
                            responseText =
                                    response.peekBody(64 * 1024).string();
                        }

                        String errorMessage =
                                getServerMessage(responseText);

                        response.close();

                        if (!success) {
                            runOnUiThread(() -> {
                                enableButtons();
                                showMessage(
                                        "Google login failed (" + code +
                                                "): " + errorMessage
                                );
                            });
                            return;
                        }

                        String firstName = account.getGivenName();
                        String lastName = account.getFamilyName();
                        String email = account.getEmail();

                        if (firstName == null) firstName = "";
                        if (lastName == null) lastName = "";
                        if (email == null) email = "";

                        final String finalFirstName = firstName;
                        final String finalLastName = lastName;
                        final String finalEmail = email;

                        runOnUiThread(() ->
                                saveUserProfile(
                                        finalFirstName,
                                        finalLastName,
                                        finalEmail,
                                        "Google login successful"
                                )
                        );
                    }
                }
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String getText(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) {
            return "";
        }

        return editText.getText().toString().trim();
    }

    private void setButtonsEnabled(boolean enabled) {
        if (btnContinue != null) {
            btnContinue.setEnabled(enabled);
        }

        if (btnGoogle != null) {
            btnGoogle.setEnabled(enabled);
        }
    }

    private void enableButtons() {
        setButtonsEnabled(true);
    }

    private void showMessage(String message) {
        Toast.makeText(
                LoginActivity.this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private String getServerMessage(String responseText) {
        try {
            JSONObject json = new JSONObject(responseText);

            String message = json.optString("msg", "");
            if (!message.isEmpty()) return message;

            message = json.optString("message", "");
            if (!message.isEmpty()) return message;

            message = json.optString("error_description", "");
            if (!message.isEmpty()) return message;

            message = json.optString("error", "");
            if (!message.isEmpty()) return message;

        } catch (Exception ignored) {
            // The response might not contain JSON.
        }

        return "Please check the server response and Supabase settings.";
    }

    private void openHome() {
        Intent intent = new Intent(
                LoginActivity.this,
                MainActivity.class
        );

        startActivity(intent);
        finish();
    }
}
