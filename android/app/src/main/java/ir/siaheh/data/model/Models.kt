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
    val username: String,
    @Json(name = "display_name") val displayName: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val about: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PublicUser(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val about: String = "",
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    @Json(name = "display_name") val displayName: String? = null,
    val username: String? = null,
    val about: String? = null,
)

@JsonClass(generateAdapter = true)
data class UserSearchResponse(val users: List<PublicUser>)

@JsonClass(generateAdapter = true)
data class DiscoverContactsRequest(val phones: List<String>)

@JsonClass(generateAdapter = true)
data class DiscoverContactsResponse(val users: List<PublicUser>)

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
data class SpaceMember(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val user: PublicUser,
    val role: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "can_read_history") val canReadHistory: Boolean,
    @Json(name = "is_pinned") val isPinned: Boolean,
    @Json(name = "is_hidden") val isHidden: Boolean,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "removed_at") val removedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class Space(
    val id: String,
    val title: String,
    val description: String = "",
    val kind: String = "space",
    @Json(name = "members_count") val membersCount: Int = 0,
    val role: String? = null,
    @Json(name = "is_pinned") val isPinned: Boolean = false,
    @Json(name = "is_hidden") val isHidden: Boolean = false,
    @Json(name = "latest_entry_preview") val latestEntryPreview: String = "",
    val members: List<SpaceMember> = emptyList(),
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
)

@JsonClass(generateAdapter = true)
data class SpacesResponse(val spaces: List<Space>)

@JsonClass(generateAdapter = true)
data class SpaceCreateRequest(
    val title: String,
    val description: String? = null,
    @Json(name = "member_ids") val memberIds: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class SpaceMemberAddRequest(
    @Json(name = "user_id") val userId: String? = null,
    val username: String? = null,
    val role: String = "member",
)

@JsonClass(generateAdapter = true)
data class SpaceMemberUpdateRequest(
    val role: String? = null,
    @Json(name = "can_read_history") val canReadHistory: Boolean? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class SpacePreferencesRequest(
    @Json(name = "is_pinned") val isPinned: Boolean? = null,
    @Json(name = "is_hidden") val isHidden: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class SpaceMembersResponse(@Json(name = "members") val members: List<SpaceMember>)

@JsonClass(generateAdapter = true)
data class EntryAttachment(
    val id: String,
    val kind: String,
    @Json(name = "original_name") val originalName: String = "",
    @Json(name = "sort_order") val sortOrder: Int = 0,
    @Json(name = "file_url") val fileUrl: String,
)

@JsonClass(generateAdapter = true)
data class SpaceEntry(
    val id: String,
    @Json(name = "space_id") val spaceId: String,
    @Json(name = "sender_id") val senderId: String,
    val sender: PublicUser,
    val text: String,
    val attachments: List<EntryAttachment> = emptyList(),
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String = "",
)

@JsonClass(generateAdapter = true)
data class EntriesPage(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: EntriesResult,
)

@JsonClass(generateAdapter = true)
data class EntriesResult(val entries: List<SpaceEntry>)

@JsonClass(generateAdapter = true)
data class SpaceConvertRequest(val confirm: Boolean = true)
