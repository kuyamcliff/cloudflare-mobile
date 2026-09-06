package dev.cfmobile.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.cfmobile.app.AppContainer
import dev.cfmobile.app.core.security.BiometricAuthenticator
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.ui.analytics.AnalyticsScreen
import dev.cfmobile.app.ui.analytics.AnalyticsViewModel
import dev.cfmobile.app.ui.botmanagement.BotManagementScreen
import dev.cfmobile.app.ui.botmanagement.BotManagementViewModel
import dev.cfmobile.app.ui.caching.CachingScreen
import dev.cfmobile.app.ui.caching.CachingViewModel
import dev.cfmobile.app.ui.dashboard.DashboardScreen
import dev.cfmobile.app.ui.dashboard.DashboardViewModel
import dev.cfmobile.app.ui.dns.DnsScreen
import dev.cfmobile.app.ui.dns.DnsViewModel
import dev.cfmobile.app.ui.firewall.FirewallScreen
import dev.cfmobile.app.ui.firewall.FirewallViewModel
import dev.cfmobile.app.ui.login.LoginScreen
import dev.cfmobile.app.ui.login.LoginViewModel
import dev.cfmobile.app.ui.pagerules.PageRulesScreen
import dev.cfmobile.app.ui.pagerules.PageRulesViewModel
import dev.cfmobile.app.ui.security.SecurityScreen
import dev.cfmobile.app.ui.security.SecurityViewModel
import dev.cfmobile.app.ui.settings.SettingsScreen
import dev.cfmobile.app.ui.settings.SettingsViewModel
import dev.cfmobile.app.ui.ssl.SslScreen
import dev.cfmobile.app.ui.ssl.SslViewModel
import dev.cfmobile.app.ui.accountmembers.AccountMembersScreen
import dev.cfmobile.app.ui.accountmembers.AccountMembersViewModel
import dev.cfmobile.app.ui.auditlogs.AuditLogsScreen
import dev.cfmobile.app.ui.auditlogs.AuditLogsViewModel
import dev.cfmobile.app.ui.loadbalancing.LoadBalancingScreen
import dev.cfmobile.app.ui.loadbalancing.LoadBalancingViewModel
import dev.cfmobile.app.ui.kv.KvKeysScreen
import dev.cfmobile.app.ui.kv.KvKeysViewModel
import dev.cfmobile.app.ui.kv.KvScreen
import dev.cfmobile.app.ui.kv.KvViewModel
import dev.cfmobile.app.ui.d1.D1ConsoleScreen
import dev.cfmobile.app.ui.d1.D1ConsoleViewModel
import dev.cfmobile.app.ui.d1.D1Screen
import dev.cfmobile.app.ui.d1.D1ViewModel
import dev.cfmobile.app.ui.rules.CacheRulesScreen
import dev.cfmobile.app.ui.rules.CacheRulesViewModel
import dev.cfmobile.app.ui.rules.ManagedRulesetsScreen
import dev.cfmobile.app.ui.rules.ManagedRulesetsViewModel
import dev.cfmobile.app.ui.rules.OriginRulesScreen
import dev.cfmobile.app.ui.rules.OriginRulesViewModel
import dev.cfmobile.app.ui.rules.RedirectRulesScreen
import dev.cfmobile.app.ui.rules.RedirectRulesViewModel
import dev.cfmobile.app.ui.account.ApiTokensScreen
import dev.cfmobile.app.ui.account.ApiTokensViewModel
import dev.cfmobile.app.ui.account.BulkRedirectsScreen
import dev.cfmobile.app.ui.account.BulkRedirectsViewModel
import dev.cfmobile.app.ui.account.NotificationsScreen
import dev.cfmobile.app.ui.account.NotificationsViewModel
import dev.cfmobile.app.ui.account.RegistrarScreen
import dev.cfmobile.app.ui.account.RegistrarViewModel
import dev.cfmobile.app.ui.account.WebAnalyticsScreen
import dev.cfmobile.app.ui.account.WebAnalyticsViewModel
import dev.cfmobile.app.ui.zerotrust.AccessIdentityScreen
import dev.cfmobile.app.ui.zerotrust.AccessIdentityViewModel
import dev.cfmobile.app.ui.zerotrust.GatewayListsScreen
import dev.cfmobile.app.ui.zerotrust.GatewayListsViewModel
import dev.cfmobile.app.ui.workerroutes.WorkerRoutesScreen
import dev.cfmobile.app.ui.workerroutes.WorkerRoutesViewModel
import dev.cfmobile.app.ui.workers.WorkersScreen
import dev.cfmobile.app.ui.workers.WorkersViewModel
import dev.cfmobile.app.ui.pages.PagesScreen
import dev.cfmobile.app.ui.pages.PagesViewModel
import dev.cfmobile.app.ui.access.AccessScreen
import dev.cfmobile.app.ui.access.AccessViewModel
import dev.cfmobile.app.ui.gateway.GatewayScreen
import dev.cfmobile.app.ui.gateway.GatewayViewModel
import dev.cfmobile.app.ui.tunnels.TunnelsScreen
import dev.cfmobile.app.ui.tunnels.TunnelsViewModel
import dev.cfmobile.app.ui.queues.QueuesScreen
import dev.cfmobile.app.ui.queues.QueuesViewModel
import dev.cfmobile.app.ui.durableobjects.DurableObjectsScreen
import dev.cfmobile.app.ui.durableobjects.DurableObjectsViewModel
import dev.cfmobile.app.ui.workflows.WorkflowsScreen
import dev.cfmobile.app.ui.workflows.WorkflowsViewModel
import dev.cfmobile.app.ui.hyperdrive.HyperdriveScreen
import dev.cfmobile.app.ui.hyperdrive.HyperdriveViewModel
import dev.cfmobile.app.ui.vectorize.VectorizeScreen
import dev.cfmobile.app.ui.vectorize.VectorizeViewModel
import dev.cfmobile.app.ui.stream.StreamScreen
import dev.cfmobile.app.ui.stream.StreamViewModel
import dev.cfmobile.app.ui.images.ImagesScreen
import dev.cfmobile.app.ui.images.ImagesViewModel
import dev.cfmobile.app.ui.turnstile.TurnstileScreen
import dev.cfmobile.app.ui.turnstile.TurnstileViewModel
import dev.cfmobile.app.ui.logpush.LogpushScreen
import dev.cfmobile.app.ui.logpush.LogpushViewModel
import dev.cfmobile.app.ui.workersai.WorkersAiScreen
import dev.cfmobile.app.ui.workersai.WorkersAiViewModel
import dev.cfmobile.app.ui.deviceposture.DevicePostureScreen
import dev.cfmobile.app.ui.deviceposture.DevicePostureViewModel
import dev.cfmobile.app.ui.securityevents.SecurityEventsScreen
import dev.cfmobile.app.ui.securityevents.SecurityEventsViewModel
import dev.cfmobile.app.ui.pageshield.PageShieldScreen
import dev.cfmobile.app.ui.pageshield.PageShieldViewModel
import dev.cfmobile.app.ui.ddos.DdosScreen
import dev.cfmobile.app.ui.ddos.DdosViewModel
import dev.cfmobile.app.ui.apishield.ApiShieldScreen
import dev.cfmobile.app.ui.apishield.ApiShieldViewModel
import dev.cfmobile.app.ui.emailrouting.EmailRoutingScreen
import dev.cfmobile.app.ui.emailrouting.EmailRoutingViewModel
import dev.cfmobile.app.ui.spectrum.SpectrumScreen
import dev.cfmobile.app.ui.spectrum.SpectrumViewModel
import dev.cfmobile.app.ui.certificates.CertificatesScreen
import dev.cfmobile.app.ui.certificates.CertificatesViewModel
import dev.cfmobile.app.ui.waitingroom.WaitingRoomScreen
import dev.cfmobile.app.ui.waitingroom.WaitingRoomViewModel
import dev.cfmobile.app.ui.healthchecks.HealthChecksScreen
import dev.cfmobile.app.ui.healthchecks.HealthChecksViewModel
import dev.cfmobile.app.ui.zonesettings.ZoneSettingGroups
import dev.cfmobile.app.ui.zonesettings.ZoneSettingSpec
import dev.cfmobile.app.ui.zonesettings.ZoneSettingsGroupScreen
import dev.cfmobile.app.ui.zonesettings.ZoneSettingsGroupViewModel
import dev.cfmobile.app.ui.magicnetwork.MagicNetworkScreen
import dev.cfmobile.app.ui.magicnetwork.MagicNetworkViewModel
import dev.cfmobile.app.ui.billing.BillingScreen
import dev.cfmobile.app.ui.billing.BillingViewModel
import dev.cfmobile.app.ui.browserrendering.BrowserRenderingScreen
import dev.cfmobile.app.ui.browserrendering.BrowserRenderingViewModel
import dev.cfmobile.app.ui.r2.R2Screen
import dev.cfmobile.app.ui.r2.R2ViewModel
import dev.cfmobile.app.ui.ratelimit.RateLimitScreen
import dev.cfmobile.app.ui.ratelimit.RateLimitViewModel
import dev.cfmobile.app.ui.transformrules.TransformRulesScreen
import dev.cfmobile.app.ui.transformrules.TransformRulesViewModel
import dev.cfmobile.app.ui.waf.WafScreen
import dev.cfmobile.app.ui.waf.WafViewModel
import dev.cfmobile.app.ui.zonedetail.ZoneMenuScreen
import dev.cfmobile.app.ui.zonedetail.ZoneMenuViewModel
import dev.cfmobile.app.ui.zones.ZonesScreen
import dev.cfmobile.app.ui.zones.ZonesViewModel

@Composable
fun CfNavHost(container: AppContainer, startDestination: String, authenticator: BiometricAuthenticator) {
    val navController = rememberNavController()

    /** Every account-scoped screen takes the same single {accountId} path argument, so declare
     *  that plumbing once instead of repeating the navArgument/extract dance per destination. */
    fun NavGraphBuilder.accountScreen(route: String, content: @Composable (accountId: String) -> Unit) {
        composable(route, arguments = listOf(navArgument("accountId") { type = NavType.StringType })) { backStackEntry ->
            content(backStackEntry.arguments?.getString("accountId").orEmpty())
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            val vm = viewModel<LoginViewModel>(factory = factoryOf { LoginViewModel(container.authRepository) })
            LoginScreen(vm, onLoggedIn = { navController.navigateToDashboardClearingBackStack() })
        }

        composable(Routes.DASHBOARD) {
            val vm = viewModel<DashboardViewModel>(
                factory = factoryOf { DashboardViewModel(container.zonesRepository, container.accountsRepository, container.authRepository) }
            )
            DashboardScreen(
                vm,
                onDomainsClick = { navController.navigate(Routes.ZONES) },
                onNavigate = { route -> navController.navigate(route) },
                onSecurityClick = { navController.navigate(Routes.SECURITY) },
                onManageAccountsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.ZONES) {
            val vm = viewModel<ZonesViewModel>(factory = factoryOf { ZonesViewModel(container.zonesRepository, container.authRepository, container.zonesCache) })
            ZonesScreen(
                vm,
                onBack = { navController.popBackStack() },
                onZoneClick = { zone: CfZone -> navController.navigate(Routes.zoneMenu(zone.id, zone.name)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            val vm = viewModel<SettingsViewModel>(factory = factoryOf { SettingsViewModel(container.authRepository) })
            SettingsScreen(
                vm,
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Routes.LOGIN) },
                onSignedOut = { navController.navigateToLoginClearingBackStack() },
                onSecurityClick = { navController.navigate(Routes.SECURITY) }
            )
        }

        composable(Routes.SECURITY) {
            val vm = viewModel<SecurityViewModel>(factory = factoryOf { SecurityViewModel(container.appLockState) })
            SecurityScreen(
                viewModel = vm,
                biometricAvailability = authenticator.availability(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.ZONE_MENU,
            arguments = listOf(navArgument("zoneId") { type = NavType.StringType }, navArgument("zoneName") { type = NavType.StringType })
        ) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = backStackEntry.arguments?.getString("zoneName").orEmpty()
            val vm = viewModel<ZoneMenuViewModel>(factory = factoryOf { ZoneMenuViewModel(zoneId, container.zonesRepository) })
            ZoneMenuScreen(
                zoneName = zoneName,
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onFeatureClick = { route -> navController.navigate(route) }
            )
        }

        composable(
            Routes.ACCOUNT_MEMBERS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<AccountMembersViewModel>(factory = factoryOf { AccountMembersViewModel(accountId, container.accountMembersRepository) })
            AccountMembersScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.AUDIT_LOGS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<AuditLogsViewModel>(factory = factoryOf { AuditLogsViewModel(accountId, container.auditLogsRepository) })
            AuditLogsScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.LOAD_BALANCING,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<LoadBalancingViewModel>(
                factory = factoryOf { LoadBalancingViewModel(accountId, container.loadBalancingRepository, container.zonesRepository) }
            )
            LoadBalancingScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.R2,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<R2ViewModel>(factory = factoryOf { R2ViewModel(accountId, container.r2Repository) })
            R2Screen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.KV,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<KvViewModel>(factory = factoryOf { KvViewModel(accountId, container.kvRepository) })
            KvScreen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenKeys = { namespace -> navController.navigate(Routes.kvKeys(accountId, namespace.id, namespace.title)) }
            )
        }

        composable(
            Routes.KV_KEYS,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("namespaceId") { type = NavType.StringType },
                navArgument("namespaceTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val namespaceId = backStackEntry.arguments?.getString("namespaceId").orEmpty()
            val namespaceTitle = Routes.decodeArg(backStackEntry.arguments?.getString("namespaceTitle").orEmpty())
            val vm = viewModel<KvKeysViewModel>(
                factory = factoryOf { KvKeysViewModel(accountId, namespaceId, container.kvRepository) }
            )
            KvKeysScreen(namespaceLabel = namespaceTitle, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.D1,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<D1ViewModel>(factory = factoryOf { D1ViewModel(accountId, container.d1Repository) })
            D1Screen(
                vm,
                onBack = { navController.popBackStack() },
                onOpenConsole = { database -> navController.navigate(Routes.d1Console(accountId, database.uuid, database.name)) }
            )
        }

        composable(
            Routes.D1_CONSOLE,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("databaseId") { type = NavType.StringType },
                navArgument("databaseName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val databaseId = backStackEntry.arguments?.getString("databaseId").orEmpty()
            val databaseName = Routes.decodeArg(backStackEntry.arguments?.getString("databaseName").orEmpty())
            val vm = viewModel<D1ConsoleViewModel>(
                factory = factoryOf { D1ConsoleViewModel(accountId, databaseId, container.d1Repository) }
            )
            D1ConsoleScreen(databaseName = databaseName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.API_TOKENS) {
            // User-scoped, not account-scoped: the token list belongs to whoever is signed in.
            val vm = viewModel<ApiTokensViewModel>(
                factory = factoryOf { ApiTokensViewModel(container.apiTokensRepository) }
            )
            ApiTokensScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.NOTIFICATIONS) { accountId ->
            val vm = viewModel<NotificationsViewModel>(
                factory = factoryOf { NotificationsViewModel(accountId, container.notificationsRepository) }
            )
            NotificationsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.BULK_REDIRECTS) { accountId ->
            val vm = viewModel<BulkRedirectsViewModel>(
                factory = factoryOf { BulkRedirectsViewModel(accountId, container.bulkRedirectsRepository) }
            )
            BulkRedirectsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.REGISTRAR) { accountId ->
            val vm = viewModel<RegistrarViewModel>(
                factory = factoryOf { RegistrarViewModel(accountId, container.registrarRepository) }
            )
            RegistrarScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.WEB_ANALYTICS) { accountId ->
            val vm = viewModel<WebAnalyticsViewModel>(
                factory = factoryOf { WebAnalyticsViewModel(accountId, container.webAnalyticsRepository) }
            )
            WebAnalyticsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.GATEWAY_LISTS) { accountId ->
            val vm = viewModel<GatewayListsViewModel>(
                factory = factoryOf { GatewayListsViewModel(accountId, container.gatewayRepository) }
            )
            GatewayListsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.ACCESS_IDENTITY) { accountId ->
            val vm = viewModel<AccessIdentityViewModel>(
                factory = factoryOf { AccessIdentityViewModel(accountId, container.accessRepository) }
            )
            AccessIdentityScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.WORKERS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<WorkersViewModel>(factory = factoryOf { WorkersViewModel(accountId, container.workersRepository) })
            WorkersScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.PAGES,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<PagesViewModel>(factory = factoryOf { PagesViewModel(accountId, container.pagesRepository) })
            PagesScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.ACCESS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<AccessViewModel>(factory = factoryOf { AccessViewModel(accountId, container.accessRepository) })
            AccessScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.GATEWAY,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<GatewayViewModel>(factory = factoryOf { GatewayViewModel(accountId, container.gatewayRepository) })
            GatewayScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(
            Routes.TUNNELS,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId").orEmpty()
            val vm = viewModel<TunnelsViewModel>(factory = factoryOf { TunnelsViewModel(accountId, container.tunnelsRepository) })
            TunnelsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.QUEUES) { accountId ->
            val vm = viewModel<QueuesViewModel>(factory = factoryOf { QueuesViewModel(accountId, container.queuesRepository) })
            QueuesScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.DURABLE_OBJECTS) { accountId ->
            val vm = viewModel<DurableObjectsViewModel>(factory = factoryOf { DurableObjectsViewModel(accountId, container.durableObjectsRepository) })
            DurableObjectsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.WORKFLOWS) { accountId ->
            val vm = viewModel<WorkflowsViewModel>(factory = factoryOf { WorkflowsViewModel(accountId, container.workflowsRepository) })
            WorkflowsScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.HYPERDRIVE) { accountId ->
            val vm = viewModel<HyperdriveViewModel>(factory = factoryOf { HyperdriveViewModel(accountId, container.hyperdriveRepository) })
            HyperdriveScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.VECTORIZE) { accountId ->
            val vm = viewModel<VectorizeViewModel>(factory = factoryOf { VectorizeViewModel(accountId, container.vectorizeRepository) })
            VectorizeScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.STREAM) { accountId ->
            val vm = viewModel<StreamViewModel>(factory = factoryOf { StreamViewModel(accountId, container.streamRepository) })
            StreamScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.IMAGES) { accountId ->
            val vm = viewModel<ImagesViewModel>(factory = factoryOf { ImagesViewModel(accountId, container.imagesRepository) })
            ImagesScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.TURNSTILE) { accountId ->
            val vm = viewModel<TurnstileViewModel>(factory = factoryOf { TurnstileViewModel(accountId, container.turnstileRepository) })
            TurnstileScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.LOGPUSH) { accountId ->
            val vm = viewModel<LogpushViewModel>(factory = factoryOf { LogpushViewModel(accountId, container.logpushRepository) })
            LogpushScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.WORKERS_AI) { accountId ->
            val vm = viewModel<WorkersAiViewModel>(factory = factoryOf { WorkersAiViewModel(accountId, container.workersAiRepository) })
            WorkersAiScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.DEVICE_POSTURE) { accountId ->
            val vm = viewModel<DevicePostureViewModel>(factory = factoryOf { DevicePostureViewModel(accountId, container.devicePostureRepository) })
            DevicePostureScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.MAGIC_NETWORK) { accountId ->
            val vm = viewModel<MagicNetworkViewModel>(factory = factoryOf { MagicNetworkViewModel(accountId, container.magicNetworkRepository) })
            MagicNetworkScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.BILLING) { accountId ->
            val vm = viewModel<BillingViewModel>(factory = factoryOf { BillingViewModel(accountId, container.billingRepository) })
            BillingScreen(vm, onBack = { navController.popBackStack() })
        }

        accountScreen(Routes.BROWSER_RENDERING) { accountId ->
            val vm = viewModel<BrowserRenderingViewModel>(factory = factoryOf { BrowserRenderingViewModel(accountId, container.browserRenderingRepository) })
            BrowserRenderingScreen(vm, onBack = { navController.popBackStack() })
        }

        val zoneScopedArgs = listOf(navArgument("zoneId") { type = NavType.StringType }, navArgument("zoneName") { type = NavType.StringType })

        /** Every declarative zone-settings family renders through the same screen, so the
         *  destination only differs by title and which specs it shows. */
        fun NavGraphBuilder.settingsGroupScreen(route: String, title: String, specs: List<ZoneSettingSpec>) {
            composable(route, arguments = zoneScopedArgs) { backStackEntry ->
                val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
                val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
                val vm = viewModel<ZoneSettingsGroupViewModel>(
                    key = route,
                    factory = factoryOf { ZoneSettingsGroupViewModel(zoneId, container.zoneSettingsRepository, specs) }
                )
                ZoneSettingsGroupScreen(title, zoneName, vm, onBack = { navController.popBackStack() })
            }
        }

        composable(Routes.REDIRECT_RULES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<RedirectRulesViewModel>(factory = factoryOf { RedirectRulesViewModel(zoneId, container.rulesetPhaseRepository) })
            RedirectRulesScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.ORIGIN_RULES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<OriginRulesViewModel>(factory = factoryOf { OriginRulesViewModel(zoneId, container.rulesetPhaseRepository) })
            OriginRulesScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.CACHE_RULES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<CacheRulesViewModel>(factory = factoryOf { CacheRulesViewModel(zoneId, container.rulesetPhaseRepository) })
            CacheRulesScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.MANAGED_WAF, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<ManagedRulesetsViewModel>(factory = factoryOf { ManagedRulesetsViewModel(zoneId, container.rulesetPhaseRepository) })
            ManagedRulesetsScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.WORKER_ROUTES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<WorkerRoutesViewModel>(factory = factoryOf { WorkerRoutesViewModel(zoneId, container.workersRepository) })
            WorkerRoutesScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.CERTIFICATES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<CertificatesViewModel>(factory = factoryOf { CertificatesViewModel(zoneId, container.certificatesRepository) })
            CertificatesScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.WAITING_ROOM, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<WaitingRoomViewModel>(factory = factoryOf { WaitingRoomViewModel(zoneId, container.waitingRoomRepository) })
            WaitingRoomScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.HEALTH_CHECKS, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<HealthChecksViewModel>(factory = factoryOf { HealthChecksViewModel(zoneId, container.healthChecksRepository) })
            HealthChecksScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        settingsGroupScreen(Routes.SPEED, "Speed", ZoneSettingGroups.SPEED)
        settingsGroupScreen(Routes.NETWORK, "Network", ZoneSettingGroups.NETWORK)
        settingsGroupScreen(Routes.SCRAPE_SHIELD, "Scrape Shield", ZoneSettingGroups.SCRAPE_SHIELD)
        settingsGroupScreen(Routes.CACHE_BEHAVIOUR, "Cache Behaviour", ZoneSettingGroups.CACHE_BEHAVIOUR)

        composable(Routes.EMAIL_ROUTING, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<EmailRoutingViewModel>(factory = factoryOf { EmailRoutingViewModel(zoneId, container.emailRoutingRepository) })
            EmailRoutingScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SPECTRUM, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<SpectrumViewModel>(factory = factoryOf { SpectrumViewModel(zoneId, container.spectrumRepository) })
            SpectrumScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SECURITY_EVENTS, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<SecurityEventsViewModel>(factory = factoryOf { SecurityEventsViewModel(zoneId, container.securityEventsRepository) })
            SecurityEventsScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.PAGE_SHIELD, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<PageShieldViewModel>(factory = factoryOf { PageShieldViewModel(zoneId, container.pageShieldRepository) })
            PageShieldScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DDOS, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<DdosViewModel>(factory = factoryOf { DdosViewModel(zoneId, container.ddosRepository) })
            DdosScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.API_SHIELD, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<ApiShieldViewModel>(factory = factoryOf { ApiShieldViewModel(zoneId, container.apiShieldRepository) })
            ApiShieldScreen(zoneName = zoneName, viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DNS, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<DnsViewModel>(factory = factoryOf { DnsViewModel(zoneId, container.dnsRepository) })
            DnsScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.SSL, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<SslViewModel>(factory = factoryOf { SslViewModel(zoneId, container.zoneSettingsRepository) })
            SslScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.FIREWALL, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<FirewallViewModel>(factory = factoryOf { FirewallViewModel(zoneId, container.firewallRepository) })
            FirewallScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.WAF, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<WafViewModel>(factory = factoryOf { WafViewModel(zoneId, container.wafRepository) })
            WafScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.RATE_LIMITING, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<RateLimitViewModel>(factory = factoryOf { RateLimitViewModel(zoneId, container.rateLimitRepository) })
            RateLimitScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.TRANSFORM_RULES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<TransformRulesViewModel>(factory = factoryOf { TransformRulesViewModel(zoneId, container.rulesetPhaseRepository) })
            TransformRulesScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.PAGE_RULES, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<PageRulesViewModel>(factory = factoryOf { PageRulesViewModel(zoneId, container.pageRulesRepository) })
            PageRulesScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.CACHING, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<CachingViewModel>(factory = factoryOf { CachingViewModel(zoneId, container.zoneSettingsRepository) })
            CachingScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.ANALYTICS, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<AnalyticsViewModel>(factory = factoryOf { AnalyticsViewModel(zoneId, container.analyticsRepository) })
            AnalyticsScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }

        composable(Routes.BOT_MANAGEMENT, arguments = zoneScopedArgs) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneName = Routes.decodeZoneName(backStackEntry.arguments?.getString("zoneName").orEmpty())
            val vm = viewModel<BotManagementViewModel>(factory = factoryOf { BotManagementViewModel(zoneId, container.zoneSettingsRepository) })
            BotManagementScreen(vm, zoneName = zoneName, onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateToDashboardClearingBackStack() {
    navigate(Routes.DASHBOARD) {
        popUpTo(0) { inclusive = true }
    }
}

private fun NavHostController.navigateToLoginClearingBackStack() {
    navigate(Routes.LOGIN) {
        popUpTo(0) { inclusive = true }
    }
}

/** Small helper so every screen can build a one-off ViewModel factory without a DI framework. */
private inline fun <reified VM : androidx.lifecycle.ViewModel> factoryOf(crossinline create: () -> VM) =
    viewModelFactory {
        initializer { create() }
    }
