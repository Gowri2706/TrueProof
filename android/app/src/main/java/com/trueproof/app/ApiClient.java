package com.trueproof.app;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    /**
     * IMPORTANT — CHANGE THIS to match where your FastAPI server (api.py) is running:
     *   - Real phone, same wifi as your laptop:  "http://<laptop-LAN-IP>:8000/"
     *       (find your laptop IP with `ipconfig` on Windows or `ifconfig`/`ip a` on Mac/Linux)
     *   - Android Studio Emulator:                "http://10.0.2.2:8000/"
     *   - Deployed server (e.g. Render/Railway):   "https://your-deployed-url.com/"
     */
    private static final String BASE_URL = "http://10.84.92.189:8000/";

    private static Retrofit retrofit;

    public static ApiService getApiService() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)   // model inference can take a few seconds
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
