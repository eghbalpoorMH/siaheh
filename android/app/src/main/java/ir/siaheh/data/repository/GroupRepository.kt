package ir.siaheh.data.repository

import ir.siaheh.data.api.GroupApi
import ir.siaheh.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupApi: GroupApi,
) {
    suspend fun listGroups(): List<Group> = groupApi.listGroups().groups

    suspend fun getGroup(groupId: String): Group = groupApi.getGroup(groupId)

    suspend fun createGroup(title: String, description: String?, memberIds: List<String>?): Group {
        return groupApi.createGroup(GroupCreateRequest(title = title, description = description, memberIds = memberIds))
    }

    suspend fun updateGroup(groupId: String, title: String?, description: String?): Group {
        return groupApi.updateGroup(groupId, GroupCreateRequest(title = title ?: "", description = description))
    }

    suspend fun updatePreferences(groupId: String, isPinned: Boolean?, isHidden: Boolean?): GroupMember {
        return groupApi.updatePreferences(groupId, GroupPreferencesRequest(isPinned = isPinned, isHidden = isHidden))
    }

    suspend fun listMembers(groupId: String): List<GroupMember> = groupApi.listMembers(groupId).members

    suspend fun addMember(groupId: String, userId: String, role: String = "member"): GroupMember {
        return groupApi.addMember(groupId, GroupMemberAddRequest(userId = userId, role = role))
    }

    suspend fun updateMember(
        groupId: String,
        userId: String,
        role: String? = null,
        canReadHistory: Boolean? = null,
        isActive: Boolean? = null,
    ): GroupMember {
        return groupApi.updateMember(
            groupId,
            userId,
            GroupMemberUpdateRequest(role = role, canReadHistory = canReadHistory, isActive = isActive),
        )
    }

    suspend fun removeMember(groupId: String, userId: String): GroupMember {
        return groupApi.removeMember(groupId, userId)
    }

    suspend fun listMessages(
        groupId: String,
        search: String? = null,
        sender: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): List<GroupMessage> {
        return groupApi.listMessages(groupId, search, sender, dateFrom, dateTo).messages
    }

    suspend fun sendMessage(groupId: String, text: String?): GroupMessage {
        return groupApi.sendMessage(groupId, GroupMessageRequest(text = text))
    }
}
