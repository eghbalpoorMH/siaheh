package ir.siaheh.data.api

import ir.siaheh.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface SpaceApi {
    @GET("spaces/")
    suspend fun listSpaces(): SpacesResponse

    @POST("spaces/")
    suspend fun createSpace(@Body request: SpaceCreateRequest): Space

    @GET("spaces/{spaceId}/")
    suspend fun getSpace(@Path("spaceId") spaceId: String): Space

    @PATCH("spaces/{spaceId}/")
    suspend fun updateSpace(@Path("spaceId") spaceId: String, @Body request: SpaceCreateRequest): Space

    @PATCH("spaces/{spaceId}/preferences/")
    suspend fun updatePreferences(@Path("spaceId") spaceId: String, @Body request: SpacePreferencesRequest): SpaceMember

    @GET("spaces/{spaceId}/members/")
    suspend fun listMembers(@Path("spaceId") spaceId: String): SpaceMembersResponse

    @POST("spaces/{spaceId}/members/add/")
    suspend fun addMember(@Path("spaceId") spaceId: String, @Body request: SpaceMemberAddRequest): SpaceMember

    @PATCH("spaces/{spaceId}/members/{userId}/")
    suspend fun updateMember(
        @Path("spaceId") spaceId: String,
        @Path("userId") userId: String,
        @Body request: SpaceMemberUpdateRequest,
    ): SpaceMember

    @DELETE("spaces/{spaceId}/members/{userId}/remove/")
    suspend fun removeMember(@Path("spaceId") spaceId: String, @Path("userId") userId: String): SpaceMember

    @GET("spaces/{spaceId}/messages/")
    suspend fun listMessages(
        @Path("spaceId") spaceId: String,
        @Query("search") search: String? = null,
        @Query("sender_username") senderUsername: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): EntriesPage

    @Multipart
    @POST("spaces/{spaceId}/messages/")
    suspend fun sendMessage(
        @Path("spaceId") spaceId: String,
        @Part("text") text: RequestBody?,
        @Part attachments: List<MultipartBody.Part>,
        @Part("attachment_kinds") attachmentKinds: List<RequestBody>,
    ): SpaceEntry

    @POST("spaces/{spaceId}/convert/")
    suspend fun convertPersonalSpace(
        @Path("spaceId") spaceId: String,
        @Body request: SpaceConvertRequest = SpaceConvertRequest(),
    ): Space
}
