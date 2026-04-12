package ir.siaheh.data.api

import ir.siaheh.data.model.*
import retrofit2.http.*

interface GroupApi {
    @GET("groups/")
    suspend fun listGroups(): GroupsResponse

    @POST("groups/")
    suspend fun createGroup(@Body request: GroupCreateRequest): Group

    @GET("groups/{groupId}/")
    suspend fun getGroup(@Path("groupId") groupId: String): Group

    @PATCH("groups/{groupId}/")
    suspend fun updateGroup(@Path("groupId") groupId: String, @Body request: GroupCreateRequest): Group

    @PATCH("groups/{groupId}/preferences/")
    suspend fun updatePreferences(@Path("groupId") groupId: String, @Body request: GroupPreferencesRequest): GroupMember

    @GET("groups/{groupId}/members/")
    suspend fun listMembers(@Path("groupId") groupId: String): GroupMembersResponse

    @POST("groups/{groupId}/members/add/")
    suspend fun addMember(@Path("groupId") groupId: String, @Body request: GroupMemberAddRequest): GroupMember

    @PATCH("groups/{groupId}/members/{userId}/")
    suspend fun updateMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
        @Body request: GroupMemberUpdateRequest,
    ): GroupMember

    @DELETE("groups/{groupId}/members/{userId}/remove/")
    suspend fun removeMember(@Path("groupId") groupId: String, @Path("userId") userId: String): GroupMember

    @GET("groups/{groupId}/messages/")
    suspend fun listMessages(
        @Path("groupId") groupId: String,
        @Query("search") search: String? = null,
        @Query("sender") sender: String? = null,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): GroupMessagesResponse

    @POST("groups/{groupId}/messages/")
    suspend fun sendMessage(@Path("groupId") groupId: String, @Body request: GroupMessageRequest): GroupMessage
}
