package dev.cfmobile.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import dev.cfmobile.app.ui.caching.CachingScreen
import dev.cfmobile.app.ui.caching.CachingViewModel
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
import dev.cfmobile.app.ui.zonedetail.ZoneMenuScreen
import dev.cfmobile.app.ui.zonedetail.ZoneMenuViewModel
import dev.cfmobile.app.ui.zones.ZonesScreen
import dev.cfmobile.app.ui.zones.ZonesViewModel

@Composable
fun CfNavHost(container: AppContainer, startDestination: String, authenticator: BiometricAuthenticator) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            val vm = viewModel<LoginViewModel>(factory = factoryOf { LoginViewModel(container.authRepository) })
            LoginScreen(vm, onLoggedIn = { navController.navigateToZonesClearingBackStack() })
        }

        composable(Routes.ZONES) {
            val vm = viewModel<ZonesViewModel>(factory = factoryOf { ZonesViewModel(container.zonesRepository) })
            ZonesScreen(
                vm,
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

        composable(Routes.DNS, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<DnsViewModel>(factory = factoryOf { DnsViewModel(zoneId, container.dnsRepository) })
            DnsScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.SSL, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<SslViewModel>(factory = factoryOf { SslViewModel(zoneId, container.zoneSettingsRepository) })
            SslScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.FIREWALL, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<FirewallViewModel>(factory = factoryOf { FirewallViewModel(zoneId, container.firewallRepository) })
            FirewallScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.PAGE_RULES, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<PageRulesViewModel>(factory = factoryOf { PageRulesViewModel(zoneId, container.pageRulesRepository) })
            PageRulesScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.CACHING, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<CachingViewModel>(factory = factoryOf { CachingViewModel(zoneId, container.zoneSettingsRepository) })
            CachingScreen(vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.ANALYTICS, arguments = listOf(navArgument("zoneId") { type = NavType.StringType })) { backStackEntry ->
            val zoneId = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val vm = viewModel<AnalyticsViewModel>(factory = factoryOf { AnalyticsViewModel(zoneId, container.analyticsRepository) })
            AnalyticsScreen(vm, onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateToZonesClearingBackStack() {
    navigate(Routes.ZONES) {
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
