package com.hal9000.tourmania.rest_api.files_upload_download;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FileUploadDownloadService {
    @Multipart
    @POST("tour/images/upsert/")
    Call<ResponseBody> uploadMultipleFilesDynamic(
            @Part("trSrvrId") RequestBody description,
            @Part List<MultipartBody.Part> files);

    @POST("tour/images/by_id/")
    Call<List<FileDownloadResponse>> downloadMultipleFiles(@Body List<String> tourIds);
}