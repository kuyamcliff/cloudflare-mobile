package dev.cfmobile.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.cfmobile.app.AppContainer
import dev.cfmobile.app.data.repository.TokenOwner
import dev.cfmobile.app.ui.activity.ActivityContent
import dev.cfmobile.app.ui.activity.ActivityViewModel
import dev.cfmobile.app.ui.diagnosticsapp.AppDiagnosticsScreen
import dev.cfmobile.app.ui.diagnosticsapp.AppDiagnosticsViewModel
import dev.cfmobile.app.ui.explorer.ApiCatalogScreen
import dev.cfmobile.app.ui.explorer.ApiCatalogViewModel
import dev.cfmobile.app.ui.explorer.ApiExplorerScreen
import dev.cfmobile.app.ui.explorer.ApiExplorerViewModel
import dev.cfmobile.app.ui.graphql.GraphQlScreen
import dev.cfmobile.app.ui.graphql.GraphQlViewModel
import dev.cfmobile.app.ui.home.HomeContent
import dev.cfmobile.app.ui.home.HomeViewModel
import dev.cfmobile.app.ui.home.ResourcesContent
import dev.cfmobile.app.ui.home.ResourcesViewModel
import dev.cfmobile.app.ui.r2.R2ObjectsScreen
import dev.cfmobile.app.ui.r2.R2ObjectsViewModel
import dev.cfmobile.app.ui.r2.TransfersScreen
import dev.cfmobile.app.ui.search.SearchScreen
import dev.cfmobile.app.ui.search.SearchViewModel
import dev.cfmobile.app.ui.shell.MainShell
import dev.cfmobile.app.ui.shell.MoreContent
import dev.cfmobile.app.ui.tokens.TokenAccessScreen
import dev.cfmobile.app.ui.tokens.TokenAccessViewModel
import dev.cfmobile.app.ui.tokens.TokenCreateScreen
import dev.cfmobile.app.ui.tokens.TokenCreateViewModel
import dev.cfmobile.app.ui.tokens.TokenDetailScreen
import dev.cfmobile.app.ui.tokens.TokenDetailViewModel
import kotlinx.coroutines.launch

/** The top-level shell. Its view models are scoped to this back stack entry. */
@Composable
fun MainShellRoute(container: AppContainer, navController: NavHostController) {
    val activeId by container.accountStore.activeIdFlow.collectAsState()
    val ctx by container.contextStore.state.collectAsState()
    val home = viewModel<HomeViewModel>(key = "home-$activeId", factory = factoryOf {
        HomeViewModel(container.authRepository, container.accountsRepository, container.zonesRepository, container.contextStore, container.capabilityRepository)
    })
    val resources = viewModel<ResourcesViewModel>(key = "res-$activeId", factory = factoryOf { ResourcesViewModel(container.contextStore, container.capabilityRepository) })
    val activity = viewModel<ActivityViewModel>(key = "act-$activeId", factory = factoryOf {
        ActivityViewModel(container.database.requestHistoryDao(), activeId, container::endpointRegistry)
    })
    val profile = container.authRepository.activeAccount
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var schemaRevision by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { schemaRevision = container.endpointRegistry().schemaRevision.take(12) }

    MainShell(
        profile = profile,
        profiles = container.authRepository.savedAccounts,
        networkStatus = container.networkStatus,
        connection = container.connectivity.state,
        onSwitchProfile = { id -> container.authRepository.switchTo(id) },
        onAddProfile = { navController.navigate(Routes.LOGIN) },
        onSearch = { navController.navigate(Routes.SEARCH) },
        onRefresh = { home.refresh(); scope.launch { container.capabilityRepository.discover() } },
        onReconnect = { navController.navigate(Routes.LOGIN) },
        home = { HomeContent(home) { navController.navigate(it) } },
        resources = { ResourcesContent(resources) { navController.navigate(it) } },
        activity = {
            ActivityContent(
                activity,
                onRepeat = { h -> navController.navigate(Routes.explorer(method = h.method, path = h.path, query = h.query)) },
                onOpenTransfers = { navController.navigate(Routes.TRANSFERS) },
                onOpenAuditLogs = ctx.account?.let { a -> { navController.navigate(Routes.auditLogs(a.id)) } }
            )
        },
        more = {
            MoreContent(ctx.account?.id, profile?.label, profile?.fingerprint, schemaRevision) { navController.navigate(it) }
        }
    )
}

fun NavGraphBuilder.platformScreens(container: AppContainer, navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    val profileLabel = { container.authRepository.activeAccount?.label }

    composable(
        Routes.EXPLORER,
        arguments = listOf("endpoint", "method", "path", "query").map { name -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }
    ) { entry ->
        fun arg(n: String) = entry.arguments?.getString(n)?.let(Routes::decodeArg)?.takeIf { it.isNotBlank() }
        val vm = viewModel<ApiExplorerViewModel>(factory = factoryOf {
            ApiExplorerViewModel(
                container::endpointRegistry, container.rawApiClient, container.capabilityRepository, container.database.savedRequestDao(),
                { container.contextStore.state.value }, { container.accountStore.getActiveId() },
                arg("endpoint"), arg("method"), arg("path"), arg("query")
            )
        })
        val profileId = container.accountStore.getActiveId().orEmpty()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        ApiExplorerScreen(
            viewModel = vm,
            profileLabel = profileLabel(),
            accountLabel = container.contextStore.state.value.account?.name,
            connection = container.connectivity.state,
            templates = remember(profileId) { container.database.savedRequestDao().observe(profileId, ApiExplorerViewModel.KIND_REST) },
            onDeleteTemplate = { id -> scope.launch { container.database.savedRequestDao().delete(id) } },
            onBack = back,
            onOpenCatalog = { navController.navigate(Routes.CATALOG) },
            onOpenHistory = { navController.navigate(Routes.ACTIVITY) }
        )
    }

    composable(Routes.ACTIVITY) {
        val profileId = container.accountStore.getActiveId()
        val vm = viewModel<ActivityViewModel>(factory = factoryOf { ActivityViewModel(container.database.requestHistoryDao(), profileId, container::endpointRegistry) })
        androidx.compose.material3.Scaffold(topBar = {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Request history") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = back) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }) { padding ->
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(padding)) {
                ActivityContent(
                    vm,
                    onRepeat = { h -> navController.navigate(Routes.explorer(method = h.method, path = h.path, query = h.query)) },
                    onOpenTransfers = { navController.navigate(Routes.TRANSFERS) },
                    onOpenAuditLogs = null
                )
            }
        }
    }

    composable(Routes.CATALOG) {
        val vm = viewModel<ApiCatalogViewModel>(factory = factoryOf { ApiCatalogViewModel(container::endpointRegistry, container.capabilityRepository) })
        ApiCatalogScreen(vm, onBack = back, onOpen = { id -> navController.navigate(Routes.explorer(endpointId = id)) })
    }

    composable(Routes.GRAPHQL) {
        val vm = viewModel<GraphQlViewModel>(factory = factoryOf {
            GraphQlViewModel(container.rawApiClient, container.database.savedRequestDao(), { container.contextStore.state.value }, { container.accountStore.getActiveId() })
        })
        val profileId = container.accountStore.getActiveId().orEmpty()
        GraphQlScreen(vm, saved = remember(profileId) { container.database.savedRequestDao().observe(profileId, ApiExplorerViewModel.KIND_GRAPHQL) }, onBack = back)
    }

    composable(Routes.TOKEN_ACCESS) {
        val vm = viewModel<TokenAccessViewModel>(factory = factoryOf {
            TokenAccessViewModel(container.capabilityRepository, container::endpointRegistry) { container.contextStore.state.value }
        })
        TokenAccessScreen(vm, profileLabel(), onBack = back, onOpenFeature = { navController.navigate(it) })
    }

    composable(
        Routes.TOKEN_DETAIL,
        arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("tokenId") { type = NavType.StringType })
    ) { entry ->
        val ownerArg = entry.arguments?.getString("owner").orEmpty()
        val tokenId = entry.arguments?.getString("tokenId").orEmpty()
        val owner = if (ownerArg == Routes.USER_OWNER) TokenOwner.User else TokenOwner.Account(ownerArg)
        val vm = viewModel<TokenDetailViewModel>(factory = factoryOf {
            TokenDetailViewModel(
                owner, tokenId, container.apiTokensRepository, container.authRepository,
                activeTokenId = { container.capabilityRepository.state.value.capabilities?.identity?.tokenId },
                names = {
                    val accounts = (container.accountsRepository.listAccounts() as? dev.cfmobile.app.data.remote.ApiResult.Success)?.data.orEmpty()
                    val zones = (container.zonesRepository.listZones(maxPages = 4) as? dev.cfmobile.app.data.remote.ApiResult.Success)?.data.orEmpty()
                    accounts.associate { it.id to it.name } + zones.associate { it.id to it.name }
                }
            )
        })
        TokenDetailScreen(vm, ownerLabel = if (owner is TokenOwner.User) "User token" else "Account token", profileLabel = profileLabel(), onBack = back)
    }

    composable(Routes.TOKEN_CREATE, arguments = listOf(navArgument("owner") { type = NavType.StringType })) { entry ->
        val ownerArg = entry.arguments?.getString("owner").orEmpty()
        val owner = if (ownerArg == Routes.USER_OWNER) TokenOwner.User else TokenOwner.Account(ownerArg)
        val vm = viewModel<TokenCreateViewModel>(factory = factoryOf {
            TokenCreateViewModel(owner, container.apiTokensRepository, container.accountsRepository, container.zonesRepository, container.authRepository)
        })
        TokenCreateScreen(vm, ownerLabel = if (owner is TokenOwner.User) "User token" else "Account token", isAccountOwned = owner is TokenOwner.Account, profileLabel = profileLabel(), onBack = back)
    }

    composable(
        Routes.R2_OBJECTS,
        arguments = listOf(
            navArgument("accountId") { type = NavType.StringType },
            navArgument("bucketName") { type = NavType.StringType },
            navArgument("jurisdiction") { type = NavType.StringType; defaultValue = "" }
        )
    ) { entry ->
        val accountId = entry.arguments?.getString("accountId").orEmpty()
        val bucket = Routes.decodeArg(entry.arguments?.getString("bucketName").orEmpty())
        val jurisdiction = entry.arguments?.getString("jurisdiction")?.let(Routes::decodeArg)?.takeIf { it.isNotBlank() && it != "default" }
        val context = LocalContext.current
        val vm = viewModel<R2ObjectsViewModel>(factory = factoryOf {
            R2ObjectsViewModel(
                accountId, bucket, jurisdiction, container.accountStore.getActiveId().orEmpty(), container.r2ObjectsRepository,
                container.transferRepository, container.r2Credentials, container.r2S3Client,
                { container.settings.state.value }, { container.connectivity.current() }, context.cacheDir
            )
        })
        R2ObjectsScreen(
            vm, profileLabel(), container.contextStore.state.value.account?.takeIf { it.id == accountId }?.name ?: accountId,
            onBack = back,
            onOpenSettings = { navController.navigate(Routes.r2Bucket(accountId, bucket)) },
            onOpenTransfers = { navController.navigate(Routes.TRANSFERS) }
        )
    }

    composable(Routes.TRANSFERS) {
        TransfersScreen(container.transferRepository.observe(), container.transferRepository, container.r2S3Client, onBack = back)
    }

    composable(Routes.SEARCH) {
        val vm = viewModel<SearchViewModel>(factory = factoryOf {
            SearchViewModel(container.zonesRepository, container.accountsRepository, container.authRepository, container.contextStore, container::endpointRegistry)
        })
        SearchScreen(vm, onBack = back, onNavigate = { navController.navigate(it) }, onProfileSwitched = back)
    }

    composable(Routes.DIAGNOSTICS) {
        val vm = viewModel<AppDiagnosticsViewModel>(factory = factoryOf {
            AppDiagnosticsViewModel(container.rawApiClient, container.networkStatus, container.connectivity, container.database, { container.accountStore.getActiveId() }, container::endpointRegistry)
        })
        AppDiagnosticsScreen(vm, container.networkStatus, onBack = back)
    }
}
