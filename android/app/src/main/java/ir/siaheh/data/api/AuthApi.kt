package ir.siaheh.data.api

import ir.siaheh.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

    @GET("users/me/")
    suspend fun me(): User

    @Multipart
    @PATCH("users/profile/")
    suspend fun updateProfile(
        @Part("display_name") displayName: RequestBody?,
        @Part("username") username: RequestBody?,
        @Part("about") about: RequestBody?,
        @Part avatar: MultipartBody.Part?,
    ): User

    @GET("users/search/")
    suspend fun searchUsers(@Query("q") query: String): UserSearchResponse

    @POST("users/discover-contacts/")
    suspend fun discoverContacts(@Body request: DiscoverContactsRequest): DiscoverContactsResponse
}
