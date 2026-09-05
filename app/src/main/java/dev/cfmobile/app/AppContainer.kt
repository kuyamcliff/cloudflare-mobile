package dev.cfmobile.app

import android.content.Context
import dev.cfmobile.app.data.local.TokenStore
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AnalyticsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.data.repository.FirewallRepository
import dev.cfmobile.app.data.repository.PageRulesRepository
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.data.repository.ZonesRepository

/** Simple hand-rolled service locator: this app is small enough that a DI framework would
 *  add more ceremony than it saves. Every repository is built once and shared. */
class AppContainer(context: Context) {
    val tokenStore = TokenStore.create(context.applicationContext)

    private val api = NetworkModule.createApi { tokenStore.getActive()?.token }
    private val verifierApi = NetworkModule.createVerifierApi()

    val authRepository = AuthRepository(verifierApi, tokenStore)
    val accountsRepository = AccountsRepository(api)
    val zonesRepository = ZonesRepository(api)
    val dnsRepository = DnsRepository(api)
    val zoneSettingsRepository = ZoneSettingsRepository(api)
    val firewallRepository = FirewallRepository(api)
    val pageRulesRepository = PageRulesRepository(api)
    val analyticsRepository = AnalyticsRepository(api)
}
