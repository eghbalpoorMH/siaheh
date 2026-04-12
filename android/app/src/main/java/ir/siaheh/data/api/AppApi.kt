package ir.siaheh.data.api

import ir.siaheh.data.model.AppVersionResponse
import ir.siaheh.data.model.OtpChannelsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AppApi {
    @GET("app-version/")
    suspend fun getAppVersion(@Query("store") store: String): AppVersionResponse

    @GET("otp-channels/")
    suspend fun getOtpChannels(): OtpChannelsResponse
}
