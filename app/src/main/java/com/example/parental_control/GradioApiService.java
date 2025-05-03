package com.example.parental_control;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface GradioApiService {
    @POST("/")
    Call<GradioResponse> classifyText(@Body GradioRequest request);
}
