package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.SecretStore
import dev.cfmobile.app.data.remote.dto.SecretStoreWrite
import dev.cfmobile.app.data.remote.dto.StoredSecret
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * The account's Secrets Store. As with Worker secrets, a value is never returned by any read -
 * only names, scopes, and status - and this app doesn't write one either: creating a secret
 * takes a bulk payload whose shape differs per scope.
 */
class SecretsStoreRepository(private val api: CloudflareApi) {

    suspend fun listStores(accountId: String): ApiResult<List<SecretStore>> =
        safeApiCall { api.listSecretStores(accountId) }

    /** Cloudflare answers the create with a list, since it accepts several at once. */
    suspend fun createStore(accountId: String, name: String): ApiResult<List<SecretStore>> =
        safeApiCall { api.createSecretStore(accountId, SecretStoreWrite(name)) }

    suspend fun deleteStore(accountId: String, storeId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteSecretStore(accountId, storeId) }

    suspend fun listSecrets(accountId: String, storeId: String): ApiResult<List<StoredSecret>> =
        safeApiCall { api.listStoredSecrets(accountId, storeId) }

    suspend fun deleteSecret(accountId: String, storeId: String, secretId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteStoredSecret(accountId, storeId, secretId) }
}
