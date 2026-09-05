package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccountMember
import dev.cfmobile.app.data.remote.dto.AccountMemberInvite
import dev.cfmobile.app.data.remote.dto.AccountRole
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

class AccountMembersRepository(private val api: CloudflareApi) {
    suspend fun listMembers(accountId: String): ApiResult<List<AccountMember>> =
        safeApiCall { api.listAccountMembers(accountId) }

    suspend fun listRoles(accountId: String): ApiResult<List<AccountRole>> =
        safeApiCall { api.listAccountRoles(accountId) }

    suspend fun inviteMember(accountId: String, email: String, roleIds: List<String>): ApiResult<AccountMember> =
        safeApiCall { api.inviteAccountMember(accountId, AccountMemberInvite(email = email, roles = roleIds)) }

    suspend fun removeMember(accountId: String, memberId: String): ApiResult<Unit> =
        safeApiCallUnit { api.removeAccountMember(accountId, memberId) }
}
