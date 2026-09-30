package dev.cfmobile.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.api.RawApiClient
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.data.local.db.RequestHistoryEntity
import dev.cfmobile.app.data.remote.CloudflareHosts
import dev.cfmobile.app.data.remote.NetworkStatus
import dev.cfmobile.app.data.remote.RequestRecorder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import dev.cfmobile.app.core.security.AppLockPreferences
import dev.cfmobile.app.core.security.AppLockState
import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.db.CfDatabase
import dev.cfmobile.app.data.local.db.ZonesCache
import dev.cfmobile.app.data.remote.NetworkModule
import dev.cfmobile.app.data.repository.AccountMembersRepository
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AddressingRepository
import dev.cfmobile.app.data.repository.AiGatewayRepository
import dev.cfmobile.app.data.repository.AnalyticsRepository
import dev.cfmobile.app.data.repository.ApiTokensRepository
import dev.cfmobile.app.data.repository.BotManagementRepository
import dev.cfmobile.app.data.repository.CallsRepository
import dev.cfmobile.app.data.repository.DiagnosticsRepository
import dev.cfmobile.app.data.repository.DnsFirewallRepository
import dev.cfmobile.app.data.repository.MagicFirewallRepository
import dev.cfmobile.app.data.repository.Web3Repository
import dev.cfmobile.app.data.repository.ZoneDnsSettingsRepository
import dev.cfmobile.app.data.repository.BulkRedirectsRepository
import dev.cfmobile.app.data.repository.CloudConnectorRepository
import dev.cfmobile.app.data.repository.CustomPagesRepository
import dev.cfmobile.app.data.repository.NotificationsRepository
import dev.cfmobile.app.data.repository.MutualTlsRepository
import dev.cfmobile.app.data.repository.PerformanceRepository
import dev.cfmobile.app.data.repository.PipelinesRepository
import dev.cfmobile.app.data.repository.RegistrarRepository
import dev.cfmobile.app.data.repository.SecretsStoreRepository
import dev.cfmobile.app.data.repository.SnippetsRepository
import dev.cfmobile.app.data.repository.WebAnalyticsRepository
import dev.cfmobile.app.data.repository.ZarazRepository
import dev.cfmobile.app.data.repository.ZoneFirewallLegacyRepository
import dev.cfmobile.app.data.repository.ZoneOwnershipRepository
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
import dev.cfmobile.app.data.repository.ApiShieldRepository
import dev.cfmobile.app.data.repository.DdosRepository
import dev.cfmobile.app.data.repository.PageShieldRepository
import dev.cfmobile.app.data.repository.BillingRepository
import dev.cfmobile.app.data.repository.BrowserRenderingRepository
import dev.cfmobile.app.data.repository.EmailRoutingRepository
import dev.cfmobile.app.data.repository.MagicNetworkRepository
import dev.cfmobile.app.data.repository.CertificatesRepository
import dev.cfmobile.app.data.repository.HealthChecksRepository
import dev.cfmobile.app.data.repository.SecurityEventsRepository
import dev.cfmobile.app.data.repository.WaitingRoomRepository
import dev.cfmobile.app.data.repository.SpectrumRepository
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
import dev.cfmobile.app.data.repository.RulesetPhaseRepository
import dev.cfmobile.app.data.repository.WafRepository
import dev.cfmobile.app.data.repository.ZoneSettingsRepository
import dev.cfmobile.app.data.repository.ZonesRepository

/** Simple hand-rolled service locator: this app is small enough that a DI framework would
 *  add more ceremony than it saves. Every repository is built once and shared. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val isDebuggable: Boolean = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** App-lifetime scope for work that outlives a screen (history writes, discovery). Errors
     *  are logged by class name only, never with a message that could carry data. */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            if (isDebuggable) Log.w("CfControl", "Background task failed: ${e.javaClass.simpleName}")
        }
    )

    val accountStore = AccountStore.create(appContext)
    val appLockState = AppLockState(AppLockPreferences.create(appContext))
    val settings = dev.cfmobile.app.core.security.AppSettings.create(appContext)
    val contextStore = dev.cfmobile.app.data.local.ContextStore.create(appContext).also { it.load(accountStore.getActiveId()) }
    val database = CfDatabase.create(appContext)

    val hosts = CloudflareHosts()
    val connectivity = dev.cfmobile.app.core.net.ConnectivityMonitor(appContext)
    val networkStatus = NetworkStatus()

    /** Loaded once, off the main thread, from the schema-generated asset. */
    private val registryDeferred: Deferred<EndpointRegistry> = appScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
        appContext.assets.open(EndpointRegistry.ASSET_NAME).use { EndpointRegistry.load(it) }
    }
    suspend fun endpointRegistry(): EndpointRegistry = registryDeferred.await()

    private val historyRecorder = RequestRecorder { record ->
        val profile = accountStore.getActiveId() ?: return@RequestRecorder
        appScope.launch {
            database.requestHistoryDao().insert(
                RequestHistoryEntity(
                    profileId = profile,
                    method = record.method,
                    path = record.path,
                    query = record.query,
                    statusCode = record.statusCode,
                    durationMillis = record.durationMillis,
                    timestamp = record.timestamp,
                    errorClass = record.errorClass
                )
            )
        }
        capabilityRepository.onRequestObserved(record)
    }

    val httpClient = NetworkModule.createClient(
        NetworkModule.Options(
            hosts = hosts,
            recorder = historyRecorder,
            status = networkStatus,
            debugLogging = isDebuggable,
            logSink = { line -> Log.d("CfHttp", line) }
        ),
        activeTokenProvider = { accountStore.getActiveToken() }
    )

    private val api = NetworkModule.createRetrofitApi(httpClient, NetworkModule.BASE_URL)
    private val verifierApi = NetworkModule.createRetrofitApi(
        NetworkModule.createClient(NetworkModule.Options(hosts = hosts), activeTokenProvider = { null }),
        NetworkModule.BASE_URL
    )
    val rawApiClient = RawApiClient(httpClient, hosts)

    val r2ObjectsRepository = dev.cfmobile.app.data.repository.R2ObjectsRepository(api)
    val r2Credentials = dev.cfmobile.app.core.transfers.R2CredentialStore.create(appContext)

    /** Separate client for R2's S3 endpoint: no Cloudflare token interceptor at all, and every
     *  request is checked against the R2 host pattern inside [dev.cfmobile.app.core.transfers.R2S3Client]. */
    val r2S3Client = dev.cfmobile.app.core.transfers.R2S3Client(
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    )
    val transferRepository = dev.cfmobile.app.core.transfers.TransferRepository(appContext, database.transferDao(), r2Credentials)

    val secureClipboard = dev.cfmobile.app.core.security.SecureClipboard(
        appContext,
        kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        clearAfterMillis = { settings.state.value.clipboardClearSeconds * 1000L }
    )

    /** Removes everything this app stored for a profile: secret, context, capability cache,
     *  request history and R2 S3 keys. Cloudflare itself is not affected (spec 340, 341). */
    suspend fun forgetProfileData(profileId: String) {
        contextStore.forgetProfile(profileId)
        capabilityRepository.forgetProfile(profileId)
        r2Credentials.removeProfile(profileId)
        database.requestHistoryDao().clear(profileId)
    }

    val localDataActions = object : dev.cfmobile.app.ui.settings.LocalDataActions {
        override suspend fun forgetProfile(profileId: String) = forgetProfileData(profileId)
        override suspend fun clearCache() {
            database.zoneDao().clearAll()
            appContext.getSharedPreferences("cf_capabilities", Context.MODE_PRIVATE).edit().clear().apply()
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
        override suspend fun clearHistory() = database.requestHistoryDao().clearAll()
        override suspend fun clearSavedRequests() = database.savedRequestDao().clearAll()
        override suspend fun clearEverything() {
            androidx.work.WorkManager.getInstance(appContext).cancelAllWorkByTag(dev.cfmobile.app.core.transfers.TransferRepository.TAG)
            kotlinx.coroutines.withContext(Dispatchers.IO) { database.clearAllTables() }
            clearCache()
            r2Credentials.clearAll()
            contextStore.clearAll()
            settings.update { dev.cfmobile.app.core.security.AppSettingsSnapshot() }
        }
    }

    fun startProfileTracking() {
        appScope.launch {
            accountStore.activeIdFlow.collect { id ->
                contextStore.load(id)
                capabilityRepository.onProfileActivated()
            }
        }
        appScope.launch {
            val days = settings.state.value.historyRetentionDays
            database.requestHistoryDao().deleteOlderThan(System.currentTimeMillis() - days * 86_400_000L)
        }
    }

    val capabilityRepository: CapabilityRepository by lazy {
        CapabilityRepository(
            api = api,
            registryProvider = { endpointRegistry() },
            cache = appContext.getSharedPreferences("cf_capabilities", Context.MODE_PRIVATE),
            scope = appScope,
            activeProfileId = { accountStore.getActiveId() }
        )
    }

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
    val securityEventsRepository = SecurityEventsRepository(api)
    val pageShieldRepository = PageShieldRepository(api)
    val ddosRepository = DdosRepository(api)
    val apiShieldRepository = ApiShieldRepository(api)
    val emailRoutingRepository = EmailRoutingRepository(api)
    val spectrumRepository = SpectrumRepository(api)
    val magicNetworkRepository = MagicNetworkRepository(api)
    val billingRepository = BillingRepository(api)
    val browserRenderingRepository = BrowserRenderingRepository(api)
    val certificatesRepository = CertificatesRepository(api)
    val waitingRoomRepository = WaitingRoomRepository(api)
    val healthChecksRepository = HealthChecksRepository(api)
    val wafRepository = WafRepository(api)
    val rateLimitRepository = RateLimitRepository(api)
    val rulesetPhaseRepository = RulesetPhaseRepository(api)
    val pageRulesRepository = PageRulesRepository(api)
    val analyticsRepository = AnalyticsRepository(api)
    val apiTokensRepository = ApiTokensRepository(api)
    val notificationsRepository = NotificationsRepository(api)
    val bulkRedirectsRepository = BulkRedirectsRepository(api)
    val registrarRepository = RegistrarRepository(api)
    val webAnalyticsRepository = WebAnalyticsRepository(api)
    val snippetsRepository = SnippetsRepository(api)
    val cloudConnectorRepository = CloudConnectorRepository(api)
    val customPagesRepository = CustomPagesRepository(api)
    val zarazRepository = ZarazRepository(api)
    val botManagementRepository = BotManagementRepository(api)
    val zoneFirewallLegacyRepository = ZoneFirewallLegacyRepository(api)
    val mutualTlsRepository = MutualTlsRepository(api)
    val zoneOwnershipRepository = ZoneOwnershipRepository(api)
    val performanceRepository = PerformanceRepository(api)
    val aiGatewayRepository = AiGatewayRepository(api)
    val callsRepository = CallsRepository(api)
    val pipelinesRepository = PipelinesRepository(api)
    val secretsStoreRepository = SecretsStoreRepository(api)
    val dnsFirewallRepository = DnsFirewallRepository(api)
    val addressingRepository = AddressingRepository(api)
    val magicFirewallRepository = MagicFirewallRepository(api)
    val diagnosticsRepository = DiagnosticsRepository(api)
    val web3Repository = Web3Repository(api)
    val zoneDnsSettingsRepository = ZoneDnsSettingsRepository(api)
}
