package com.trueproof.app;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.Part;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Streaming;
import okhttp3.ResponseBody;

/**
 * Matches the endpoints exposed by python_ai/api.py
 */
public interface ApiService {

    @Multipart
    @retrofit2.http.POST("scan")
    Call<ScanResponse> scanScreenshot(@Part MultipartBody.Part file);

    @Streaming
    @GET("report/{scanId}")
    Call<ResponseBody> downloadReport(@Path("scanId") String scanId);
}
