package ir.siaheh.data.api

import ir.siaheh.data.model.*
import retrofit2.http.*

interface AuthApi {
    @POST("auth/otp/")
    suspend fun requestOtp(@Body request: OtpRequest): OtpResponse

    @POST("auth/tokens/")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): TokensResponse

    @PUT("auth/tokens/")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @HTTP(method = "DELETE", path = "auth/tokens/", hasBody = true)
    suspend fun logout(@Body request: RefreshTokenRequest)
}
