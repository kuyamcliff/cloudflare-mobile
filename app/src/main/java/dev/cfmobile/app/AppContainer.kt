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
import dev.cfmobile.app.data.repository.AuditLogsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.D1Repository
import dev.cfmobile.app.data.repository.DnsRepository
import dev.cfmobile.app.data.repository.AccessRepository
import dev.cfmobile.app.data.repository.GatewayRepository
import dev.cfmobile.app.data.repository.DurableObjectsRepository
import dev.cfmobile.app.data.repository.HyperdriveRepository
import dev.cfmobile.app.data.repository.QueuesRepository
import dev.cfmobile.app.data.repository.TunnelsRepository
import dev.cfmobile.app.data.repository.DevicePostureRepository
import dev.cfmobile.app.data.repository.ImagesRepository
import dev.cfmobile.app.data.repository.LogpushRepository
import dev.cfmobile.app.data.repository.StreamRepository
import dev.cfmobile.app.data.repository.TurnstileRepository
import dev.cfmobile.app.data.repository.VectorizeRepository
import dev.cfmobile.app.data.repository.WorkersAiRepository
import dev.cfmobile.app.data.repository.WorkflowsRepository
import dev.cfmobile.app.data.repository.PagesRepository
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.data.repository.FirewallRepository
import dev.cfmobile.app.data.repository.KvRepository
import dev.cfmobile.app.data.repository.LoadBalancingRepository
import dev.cfmobile.app.data.repository.PageRulesRepository
import dev.cfmobile.app.data.repository.R2Repository
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
    val auditLogsRepository = AuditLogsRepository(api)
    val zonesRepository = ZonesRepository(api)
    val zonesCache = ZonesCache(database.zoneDao())
    val dnsRepository = DnsRepository(api)
    val zoneSettingsRepository = ZoneSettingsRepository(api)
    val firewallRepository = FirewallRepository(api)
    val loadBalancingRepository = LoadBalancingRepository(api)
    val r2Repository = R2Repository(api)
    val kvRepository = KvRepository(api)
    val d1Repository = D1Repository(api)
    val workersRepository = WorkersRepository(api)
    val pagesRepository = PagesRepository(api)
    val accessRepository = AccessRepository(api)
    val gatewayRepository = GatewayRepository(api)
    val tunnelsRepository = TunnelsRepository(api)
    val queuesRepository = QueuesRepository(api)
    val durableObjectsRepository = DurableObjectsRepository(api)
    val workflowsRepository = WorkflowsRepository(api)
    val hyperdriveRepository = HyperdriveRepository(api)
    val vectorizeRepository = VectorizeRepository(api)
    val streamRepository = StreamRepository(api)
    val imagesRepository = ImagesRepository(api)
    val turnstileRepository = TurnstileRepository(api)
    val logpushRepository = LogpushRepository(api)
    val workersAiRepository = WorkersAiRepository(api)
    val devicePostureRepository = DevicePostureRepository(api)
    val wafRepository = WafRepository(api)
    val rateLimitRepository = RateLimitRepository(api)
    val transformRulesRepository = TransformRulesRepository(api)
    val pageRulesRepository = PageRulesRepository(api)
    val analyticsRepository = AnalyticsRepository(api)
}
