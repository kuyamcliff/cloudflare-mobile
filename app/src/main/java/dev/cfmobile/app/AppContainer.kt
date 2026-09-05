package dev.cfmobile.app

import android.content.Context
import dev.cfmobile.app.core.security.AppLockPreferences
import dev.cfmobile.app.core.security.AppLockState
import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.db.CfDatabase
import dev.cfmobile.app.data.local.db.ZonesCache
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.data.repository.AccountMembersRepository
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AnalyticsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.data.repository.FirewallRepository
import dev.cfmobile.app.data.repository.PageRulesRepository
import dev.cfmobile.app.data.repository.RateLimitRepository
import dev.cfmobile.app.data.repository.TransformRulesRepository
import dev.cfmobile.app.data.repository.WafRepository
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.data.repository.ZonesRepository

/** Simple hand-rolled service locator: this app is small enough that a DI framework would
 *  add more ceremony than it saves. Every repository is built once and shared. */
class AppContainer(context: Context) {
    val accountStore = AccountStore.create(context.applicationContext)
    val appLockState = AppLockState(AppLockPreferences.create(context.applicationContext))
    private val database = CfDatabase.create(context.applicationContext)

    private val api = NetworkModule.createApi { accountStore.getActiveToken() }
    private val verifierApi = NetworkModule.createVerifierApi()

    val authRepository = AuthRepository(verifierApi, accountStore)
    val accountsRepository = AccountsRepository(api)
    val accountMembersRepository = AccountMembersRepository(api)
    val zonesRepository = ZonesRepository(api)
    val zonesCache = ZonesCache(database.zoneDao())
    val dnsRepository = DnsRepository(api)
    val zoneSettingsRepository = ZoneSettingsRepository(api)
    val firewallRepository = FirewallRepository(api)
    val wafRepository = WafRepository(api)
    val rateLimitRepository = RateLimitRepository(api)
    val transformRulesRepository = TransformRulesRepository(api)
    val pageRulesRepository = PageRulesRepository(api)
    val analyticsRepository = AnalyticsRepository(api)
}
