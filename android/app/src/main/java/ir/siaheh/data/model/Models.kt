package ir.siaheh.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OtpRequest(val phone: String)

@JsonClass(generateAdapter = true)
data class OtpResponse(@Json(name = "expires_in") val expiresIn: Int)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val phone: String,
    val code: String,
    @Json(name = "client_platform") val clientPlatform: String? = null,
    @Json(name = "client_store") val clientStore: String? = null,
    @Json(name = "app_version_name") val appVersionName: String? = null,
    @Json(name = "app_version_code") val appVersionCode: Int? = null,
    @Json(name = "device_model") val deviceModel: String? = null,
    @Json(name = "os_version") val osVersion: String? = null,
)

@JsonClass(generateAdapter = true)
data class TokensResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "is_new_user") val isNewUser: Boolean,
    val update: LoginUpdateInfo? = null,
    val user: User,
)

@JsonClass(generateAdapter = true)
data class LoginUpdateInfo(
    val status: String = "none",
    val message: String = "",
    val store: String = "",
    @Json(name = "min_version") val minVersion: String = "",
    @Json(name = "latest_version") val latestVersion: String = "",
    @Json(name = "update_url") val updateUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(@Json(name = "refresh_token") val refreshToken: String)

@JsonClass(generateAdapter = true)
data class RefreshTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val phone: String,
    @Json(name = "created_at") val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AppVersionResponse(
    @Json(name = "latest_version") val latestVersion: String,
    @Json(name = "min_version") val minVersion: String,
    @Json(name = "update_url") val updateUrl: String,
)

@JsonClass(generateAdapter = true)
data class OtpChannel(
    val type: String,
    val name: String,
    val url: String = "",
)

@JsonClass(generateAdapter = true)
data class OtpChannelsResponse(val channels: List<OtpChannel>)

@JsonClass(generateAdapter = true)
data class GroupMember(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val phone: String,
    val role: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "can_read_history") val canReadHistory: Boolean,
    @Json(name = "is_pinned") val isPinned: Boolean,
    @Json(name = "is_hidden") val isHidden: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "removed_at") val removedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class Group(
    val id: String,
    val title: String,
    val description: String = "",
    @Json(name = "members_count") val membersCount: Int = 0,
    val role: String? = null,
    @Json(name = "is_pinned") val isPinned: Boolean = false,
    @Json(name = "is_hidden") val isHidden: Boolean = false,
    val members: List<GroupMember> = emptyList(),
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
)

@JsonClass(generateAdapter = true)
data class GroupsResponse(val groups: List<Group>)

@JsonClass(generateAdapter = true)
data class GroupCreateRequest(
    val title: String,
    val description: String? = null,
    @Json(name = "member_ids") val memberIds: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class GroupMemberAddRequest(
    @Json(name = "user_id") val userId: String,
    val role: String = "member",
)

@JsonClass(generateAdapter = true)
data class GroupMemberUpdateRequest(
    val role: String? = null,
    @Json(name = "can_read_history") val canReadHistory: Boolean? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class GroupPreferencesRequest(
    @Json(name = "is_pinned") val isPinned: Boolean? = null,
    @Json(name = "is_hidden") val isHidden: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class GroupMembersResponse(@Json(name = "members") val members: List<GroupMember>)

@JsonClass(generateAdapter = true)
data class GroupMessage(
    val id: String,
    @Json(name = "group_id") val groupId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "sender_phone") val senderPhone: String,
    val text: String,
    val image: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
)

@JsonClass(generateAdapter = true)
data class GroupMessagesResponse(@Json(name = "messages") val messages: List<GroupMessage>)

@JsonClass(generateAdapter = true)
data class GroupMessageRequest(
    val text: String? = null,
)
