package ir.siaheh.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.siaheh.data.api.SpaceApi
import ir.siaheh.data.model.Space
import ir.siaheh.data.model.SpaceEntry
import ir.siaheh.data.model.SpaceMember
import ir.siaheh.data.model.SpaceMemberAddRequest
import ir.siaheh.data.model.SpaceMemberUpdateRequest
import ir.siaheh.data.model.SpacePreferencesRequest
import ir.siaheh.data.model.SpaceCreateRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceRepository @Inject constructor(
    private val spaceApi: SpaceApi,
    @ApplicationContext private val context: Context,
) {
    suspend fun listSpaces(): List<Space> = spaceApi.listSpaces().spaces

    suspend fun getSpace(spaceId: String): Space = spaceApi.getSpace(spaceId)

    suspend fun createSpace(title: String, description: String?, memberIds: List<String>?): Space {
        return spaceApi.createSpace(SpaceCreateRequest(title = title, description = description, memberIds = memberIds))
    }

    suspend fun updateSpace(spaceId: String, title: String?, description: String?): Space {
        return spaceApi.updateSpace(spaceId, SpaceCreateRequest(title = title ?: "", description = description))
    }

    suspend fun convertPersonalSpace(spaceId: String): Space = spaceApi.convertPersonalSpace(spaceId)

    suspend fun updatePreferences(spaceId: String, isPinned: Boolean?, isHidden: Boolean?): SpaceMember {
        return spaceApi.updatePreferences(spaceId, SpacePreferencesRequest(isPinned = isPinned, isHidden = isHidden))
    }

    suspend fun listMembers(spaceId: String): List<SpaceMember> = spaceApi.listMembers(spaceId).members

    suspend fun addMember(spaceId: String, username: String, role: String = "member"): SpaceMember {
        return spaceApi.addMember(spaceId, SpaceMemberAddRequest(username = username, role = role))
    }

    suspend fun updateMember(
        spaceId: String,
        userId: String,
        role: String? = null,
        canReadHistory: Boolean? = null,
        isActive: Boolean? = null,
    ): SpaceMember {
        return spaceApi.updateMember(
            spaceId,
            userId,
            SpaceMemberUpdateRequest(role = role, canReadHistory = canReadHistory, isActive = isActive),
        )
    }

    suspend fun removeMember(spaceId: String, userId: String): SpaceMember {
        return spaceApi.removeMember(spaceId, userId)
    }

    suspend fun listEntries(
        spaceId: String,
        search: String? = null,
        senderUsername: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        page: Int? = null,
    ): List<SpaceEntry> {
        return spaceApi.listMessages(spaceId, search, senderUsername, dateFrom, dateTo, page = page, pageSize = 40).results.entries
    }

    suspend fun sendEntry(
        spaceId: String,
        text: String?,
        attachments: List<Uri>,
        attachmentKinds: List<String> = emptyList(),
    ): SpaceEntry {
        val textBody = text?.trim()?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parts = attachments.mapIndexedNotNull { index, uri ->
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = inferDisplayName(uri, index)
            val tempFile = copyToCache(uri, fileName) ?: return@mapIndexedNotNull null
            val body = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("attachments", fileName, body)
        }
        val kindBodies = attachments.mapIndexed { index, uri ->
            val provided = attachmentKinds.getOrNull(index)
            val kind = provided ?: detectAttachmentKind(context.contentResolver.getType(uri))
            kind.toRequestBody("text/plain".toMediaTypeOrNull())
        }
        return spaceApi.sendMessage(spaceId, textBody, parts, kindBodies)
    }

    private fun detectAttachmentKind(mimeType: String?): String {
        val mime = mimeType.orEmpty().lowercase()
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "music"
            mime == "application/pdf" || mime.startsWith("text/") -> "document"
            else -> "file"
        }
    }

    private fun inferDisplayName(uri: Uri, index: Int): String {
        val fallback = "attachment_$index"
        val cursor = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return fallback
    }

    private fun copyToCache(uri: Uri, fileName: String): File? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val safeName = fileName.replace("[^A-Za-z0-9._-]".toRegex(), "_")
        val outFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$safeName")
        input.use { inp ->
            FileOutputStream(outFile).use { out ->
                inp.copyTo(out)
            }
        }
        return outFile
    }
}
