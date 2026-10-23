
package com.trueproof.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseAuth {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private static final String PREFS = "trueproof_prefs";

    private final OkHttpClient client;
    private final String supabaseUrl;
    private final String publishableKey;
    private final Context context;

    public SupabaseAuth(Context context) {
        this.context = context.getApplicationContext();
        this.client = new OkHttpClient();

        this.supabaseUrl =
                context.getString(R.string.supabase_url);

        this.publishableKey =
                context.getString(R.string.supabase_publishable_key);
    }

    // =========================================================
    // EMAIL + PASSWORD SIGN UP
    // =========================================================

    public void signUpWithEmailPassword(
            String firstName,
            String lastName,
            String email,
            String password,
            Callback callback
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            JSONObject data = new JSONObject();
            data.put("first_name", firstName);
            data.put("last_name", lastName);

            json.put("data", data);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/signup")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(
                    new AuthSessionCallback(email, firstName, lastName, callback)
            );

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }

    // =========================================================
    // EMAIL + PASSWORD LOGIN
    // =========================================================

    public void loginWithEmailPassword(
            String email,
            String password,
            Callback callback
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/token?grant_type=password")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(
                    new AuthSessionCallback(email, "", "", callback)
            );

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }

    // =========================================================
    // SAVE AUTH SESSION
    // =========================================================

    private class AuthSessionCallback implements Callback {

        private final String email;
        private final String firstName;
        private final String lastName;
        private final Callback originalCallback;

        AuthSessionCallback(
                String email,
                String firstName,
                String lastName,
                Callback originalCallback
        ) {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.originalCallback = originalCallback;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            originalCallback.onFailure(call, e);
        }

        @Override
        public void onResponse(Call call, Response response)
                throws IOException {

            if (response.isSuccessful() && response.body() != null) {
                try {
                    // peekBody reads a copy and keeps the original response
                    // available to the calling Activity.
                    String responseText =
                            response.peekBody(1024 * 1024).string();

                    JSONObject result = new JSONObject(responseText);

                    String accessToken =
                            result.optString("access_token", "");

                    String refreshToken =
                            result.optString("refresh_token", "");

                    JSONObject user = result.optJSONObject("user");

                    String userId = "";
                    String actualEmail = email;
                    String actualFirstName = firstName;
                    String actualLastName = lastName;

                    if (user != null) {
                        userId = user.optString("id", "");

                        String returnedEmail =
                                user.optString("email", "");

                        if (!returnedEmail.isEmpty()) {
                            actualEmail = returnedEmail;
                        }

                        JSONObject metadata =
                                user.optJSONObject("user_metadata");

                        if (metadata != null) {
                            String returnedFirst =
                                    metadata.optString("first_name", "");

                            String returnedLast =
                                    metadata.optString("last_name", "");

                            if (!returnedFirst.isEmpty()) {
                                actualFirstName = returnedFirst;
                            }

                            if (!returnedLast.isEmpty()) {
                                actualLastName = returnedLast;
                            }
                        }
                    }

                    // Signup may succeed without a session when email
                    // confirmation is enabled in the Supabase project.
                    if (!accessToken.isEmpty() && !userId.isEmpty()) {
                        SharedPreferences prefs =
                                context.getSharedPreferences(
                                        PREFS,
                                        Context.MODE_PRIVATE
                                );

                        SharedPreferences.Editor editor = prefs.edit();

                        editor.putString(
                                "supabase_access_token", accessToken
                        );
                        editor.putString(
                                "supabase_refresh_token", refreshToken
                        );
                        editor.putString("supabase_user_id", userId);
                        editor.putString("email", actualEmail);
                        editor.putString("first_name", actualFirstName);
                        editor.putString("last_name", actualLastName);
                        editor.apply();
                    }

                } catch (Exception e) {
                    response.close();
                    originalCallback.onFailure(
                            call,
                            new IOException("Could not read authentication response", e)
                    );
                    return;
                }
            }

            originalCallback.onResponse(call, response);
        }
    }

    // =========================================================
    // GOOGLE SIGN-IN
    // Keep the existing Google authentication flow
    // =========================================================

    public void signInWithGoogleIdToken(
            String idToken,
            Callback callback
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("provider", "google");
            json.put("id_token", idToken);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/token?grant_type=id_token")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onFailure(call, e);
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {

                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject result = new JSONObject(
                                    response.peekBody(1024 * 1024).string()
                            );

                            String accessToken =
                                    result.optString("access_token", "");
                            String refreshToken =
                                    result.optString("refresh_token", "");

                            JSONObject user = result.optJSONObject("user");

                            if (user != null && !accessToken.isEmpty()) {
                                String userId = user.optString("id", "");
                                String email = user.optString("email", "");

                                JSONObject metadata =
                                        user.optJSONObject("user_metadata");

                                String firstName = "";
                                String lastName = "";

                                if (metadata != null) {
                                    firstName = metadata.optString(
                                            "first_name", ""
                                    );
                                    lastName = metadata.optString(
                                            "last_name", ""
                                    );

                                    if (firstName.isEmpty()) {
                                        firstName = metadata.optString(
                                                "full_name", ""
                                        );
                                    }
                                }

                                SharedPreferences prefs =
                                        context.getSharedPreferences(
                                                PREFS,
                                                Context.MODE_PRIVATE
                                        );

                                prefs.edit()
                                        .putString(
                                                "supabase_access_token",
                                                accessToken
                                        )
                                        .putString(
                                                "supabase_refresh_token",
                                                refreshToken
                                        )
                                        .putString("supabase_user_id", userId)
                                        .putString("email", email)
                                        .putString("first_name", firstName)
                                        .putString("last_name", lastName)
                                        .apply();
                            }

                        } catch (Exception e) {
                            response.close();
                            callback.onFailure(
                                    call,
                                    new IOException(
                                            "Could not read Google login response",
                                            e
                                    )
                            );
                            return;
                        }
                    }

                    callback.onResponse(call, response);
                }
            });

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }

    // =========================================================
    // SAVE PROFILE TO SUPABASE AND LOCAL STORAGE
    // =========================================================

    public void saveProfile(
            String firstName,
            String lastName,
            String email,
            Callback callback
    ) {
        try {
            String userId = getUserId();
            String accessToken = getAccessToken();

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    );

            prefs.edit()
                    .putString("first_name", firstName)
                    .putString("last_name", lastName)
                    .putString("email", email)
                    .apply();

            if (userId.isEmpty() || accessToken.isEmpty()) {
                callback.onFailure(
                        null,
                        new IOException("Authentication session is missing")
                );
                return;
            }

            JSONObject json = new JSONObject();
            json.put("id", userId);
            json.put("first_name", firstName);
            json.put("last_name", lastName);
            json.put("email", email);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/profiles?on_conflict=id")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(
                            "Prefer",
                            "resolution=merge-duplicates,return=minimal"
                    )
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String getFirstName() {
        return getStoredValue("first_name");
    }

    public String getLastName() {
        return getStoredValue("last_name");
    }

    public String getEmail() {
        return getStoredValue("email");
    }

    public String getAccessToken() {
        return getStoredValue("supabase_access_token");
    }

    public String getRefreshToken() {
        return getStoredValue("supabase_refresh_token");
    }

    public String getUserId() {
        return getStoredValue("supabase_user_id");
    }

    private String getStoredValue(String key) {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(key, "");
    }

    // =========================================================
    // LOGIN CHECK
    // =========================================================

    public boolean isLoggedIn() {
        return !getAccessToken().isEmpty()
                && !getUserId().isEmpty();
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    public void logout() {
        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        prefs.edit()
                .remove("supabase_access_token")
                .remove("supabase_refresh_token")
                .remove("supabase_user_id")
                .remove("first_name")
                .remove("last_name")
                .remove("email")
                .apply();
    }

    // =========================================================
    // TEMPORARY COMPATIBILITY FOR THE OLD LOGIN ACTIVITY
    // Remove these methods after LoginActivity is updated.
    // =========================================================

    public void sendEmailOtp(String email, Callback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("create_user", true);

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/otp")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }

    public void verifyEmailOtp(
            String email,
            String token,
            Callback callback
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("token", token);
            json.put("type", "email");

            Request request = new Request.Builder()
                    .url(supabaseUrl + "/auth/v1/verify")
                    .post(RequestBody.create(json.toString(), JSON))
                    .addHeader("apikey", publishableKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(callback);

        } catch (Exception e) {
            callback.onFailure(null, new IOException(e));
        }
    }
}
