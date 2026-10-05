package com.trueproof.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;

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

        client = new OkHttpClient();

        supabaseUrl =
                context.getString(R.string.supabase_url);

        publishableKey =
                context.getString(
                        R.string.supabase_publishable_key
                );
    }

    // =========================================================
    // EMAIL OTP
    // =========================================================

    public void sendEmailOtp(
            String email,
            Callback callback
    ) {

        try {

            JSONObject json = new JSONObject();

            json.put("email", email);
            json.put("create_user", true);

            RequestBody body =
                    RequestBody.create(
                            json.toString(),
                            JSON
                    );

            Request request =
                    new Request.Builder()
                            .url(
                                    supabaseUrl
                                            + "/auth/v1/otp"
                            )
                            .post(body)
                            .addHeader(
                                    "apikey",
                                    publishableKey
                            )
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .build();

            client.newCall(request)
                    .enqueue(callback);

        } catch (Exception e) {

            callback.onFailure(
                    null,
                    new IOException(e)
            );
        }
    }

    // =========================================================
    // VERIFY EMAIL OTP
    // =========================================================

    public void verifyEmailOtp(
            String email,
            String token,
            Callback callback
    ) {

        try {

            JSONObject json =
                    new JSONObject();

            json.put("email", email);
            json.put("token", token);
            json.put("type", "email");

            RequestBody body =
                    RequestBody.create(
                            json.toString(),
                            JSON
                    );

            Request request =
                    new Request.Builder()
                            .url(
                                    supabaseUrl
                                            + "/auth/v1/verify"
                            )
                            .post(body)
                            .addHeader(
                                    "apikey",
                                    publishableKey
                            )
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .build();

            client.newCall(
                    request
            ).enqueue(new Callback() {

                @Override
                public void onFailure(
                        okhttp3.Call call,
                        IOException e
                ) {

                    callback.onFailure(
                            call,
                            e
                    );
                }

                @Override
                public void onResponse(
                        okhttp3.Call call,
                        Response response
                ) throws IOException {

                    if (response.isSuccessful()
                            && response.body() != null) {

                        String responseBody =
                                response.body().string();

                        try {

                            JSONObject result =
                                    new JSONObject(
                                            responseBody
                                    );

                            String accessToken =
                                    result.optString(
                                            "access_token",
                                            ""
                                    );

                            String refreshToken =
                                    result.optString(
                                            "refresh_token",
                                            ""
                                    );

                            JSONObject user =
                                    result.optJSONObject(
                                            "user"
                                    );

                            String userId = "";

                            if (user != null) {

                                userId =
                                        user.optString(
                                                "id",
                                                ""
                                        );
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
                                    .putString(
                                            "supabase_user_id",
                                            userId
                                    )
                                    .putString(
                                            "email",
                                            email
                                    )
                                    .apply();

                        } catch (Exception e) {

                            callback.onFailure(
                                    call,
                                    new IOException(e)
                            );

                            return;
                        }
                    }

                    callback.onResponse(
                            call,
                            response
                    );
                }
            });

        } catch (Exception e) {

            callback.onFailure(
                    null,
                    new IOException(e)
            );
        }
    }

    // =========================================================
    // GOOGLE SIGN-IN
    // Exchange Google ID Token with Supabase
    // =========================================================

    public void signInWithGoogleIdToken(
            String idToken,
            Callback callback
    ) {

        try {

            JSONObject json =
                    new JSONObject();

            json.put(
                    "provider",
                    "google"
            );

            json.put(
                    "id_token",
                    idToken
            );

            RequestBody body =
                    RequestBody.create(
                            json.toString(),
                            JSON
                    );

            Request request =
                    new Request.Builder()
                            .url(
                                    supabaseUrl
                                            + "/auth/v1/token?grant_type=id_token"
                            )
                            .post(body)
                            .addHeader(
                                    "apikey",
                                    publishableKey
                            )
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .build();

            client.newCall(request)
                    .enqueue(new Callback() {

                        @Override
                        public void onFailure(
                                okhttp3.Call call,
                                IOException e
                        ) {

                            callback.onFailure(
                                    call,
                                    e
                            );
                        }

                        @Override
                        public void onResponse(
                                okhttp3.Call call,
                                Response response
                        ) throws IOException {

                            if (response.isSuccessful()
                                    && response.body() != null) {

                                String responseBody =
                                        response.body().string();

                                try {

                                    JSONObject result =
                                            new JSONObject(
                                                    responseBody
                                            );

                                    String accessToken =
                                            result.optString(
                                                    "access_token",
                                                    ""
                                            );

                                    String refreshToken =
                                            result.optString(
                                                    "refresh_token",
                                                    ""
                                            );

                                    JSONObject user =
                                            result.optJSONObject(
                                                    "user"
                                            );

                                    String userId = "";
                                    String email = "";

                                    if (user != null) {

                                        userId =
                                                user.optString(
                                                        "id",
                                                        ""
                                                );

                                        email =
                                                user.optString(
                                                        "email",
                                                        ""
                                                );
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
                                            .putString(
                                                    "supabase_user_id",
                                                    userId
                                            )
                                            .putString(
                                                    "email",
                                                    email
                                            )
                                            .apply();

                                } catch (Exception e) {

                                    callback.onFailure(
                                            call,
                                            new IOException(e)
                                    );

                                    return;
                                }
                            }

                            callback.onResponse(
                                    call,
                                    response
                            );
                        }
                    });

        } catch (Exception e) {

            callback.onFailure(
                    null,
                    new IOException(e)
            );
        }
    }

    // =========================================================
    // SAVE PROFILE
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

            // -------------------------------------------------
            // SAVE PROFILE LOCALLY
            // This is used by Home screen
            // -------------------------------------------------

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS,
                            Context.MODE_PRIVATE
                    );

            prefs.edit()
                    .putString(
                            "first_name",
                            firstName
                    )
                    .putString(
                            "last_name",
                            lastName
                    )
                    .putString(
                            "email",
                            email
                    )
                    .apply();

            // -------------------------------------------------
            // SAVE PROFILE TO SUPABASE
            // -------------------------------------------------

            JSONObject json = new JSONObject();

            json.put("id", userId);
            json.put("first_name", firstName);
            json.put("last_name", lastName);
            json.put("email", email);

            RequestBody body =
                    RequestBody.create(
                            json.toString(),
                            JSON
                    );

            Request request =
                    new Request.Builder()
                            .url(
                                    supabaseUrl
                                            + "/rest/v1/profiles"
                            )
                            .post(body)
                            .addHeader(
                                    "apikey",
                                    publishableKey
                            )
                            .addHeader(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .addHeader(
                                    "Content-Type",
                                    "application/json"
                            )
                            .addHeader(
                                    "Prefer",
                                    "resolution=merge-duplicates"
                            )
                            .build();

            client.newCall(request)
                    .enqueue(callback);

        } catch (Exception e) {

            callback.onFailure(
                    null,
                    new IOException(e)
            );
        }
    }

    // =========================================================
    // GET FIRST NAME
    // =========================================================

    public String getFirstName() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "first_name",
                ""
        );
    }

    // =========================================================
    // GET LAST NAME
    // =========================================================

    public String getLastName() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "last_name",
                ""
        );
    }

    // =========================================================
    // GET EMAIL
    // =========================================================

    public String getEmail() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "email",
                ""
        );
    }

    // =========================================================
    // GET ACCESS TOKEN
    // =========================================================

    public String getAccessToken() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "supabase_access_token",
                ""
        );
    }

    // =========================================================
    // GET REFRESH TOKEN
    // =========================================================

    public String getRefreshToken() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "supabase_refresh_token",
                ""
        );
    }

    // =========================================================
    // GET USER ID
    // =========================================================

    public String getUserId() {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        return prefs.getString(
                "supabase_user_id",
                ""
        );
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
}