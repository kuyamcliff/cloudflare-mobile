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
import dev.cfmobile.app.ui.shell.ProfileScreen
import dev.cfmobile.app.ui.command.CommandScreen
import dev.cfmobile.app.ui.command.CommandViewModel
import dev.cfmobile.app.ui.command.PickerSheet
import dev.cfmobile.app.ui.action.ActionScreen
import dev.cfmobile.app.ui.action.ActionViewModel
import dev.cfmobile.app.core.command.ActionDraft
import dev.cfmobile.app.data.local.NamedRef
import dev.cfmobile.app.data.remote.ApiResult
import androidx.compose.runtime.mutableStateOf
import dev.cfmobile.app.ui.tokens.TokenAccessScreen
import dev.cfmobile.app.ui.tokens.TokenAccessViewModel
import dev.cfmobile.app.ui.tokens.TokenCreateScreen
import dev.cfmobile.app.ui.tokens.TokenCreateViewModel
import dev.cfmobile.app.ui.tokens.TokenDetailScreen
import dev.cfmobile.app.ui.tokens.TokenDetailViewModel
import kotlinx.coroutines.launch

/** The top-level shell. Its view models are scoped to this back stack entry. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val pins by container.actionStore.pins.collectAsState()
    val recents by container.actionStore.recents.collectAsState()
    val homeUi by home.uiState.collectAsState()
    var accountSheet by remember { mutableStateOf(false) }

    MainShell(
        profile = container.authRepository.activeAccount,
        accountName = ctx.account?.name,
        networkStatus = container.networkStatus,
        connection = container.connectivity.state,
        onSwitchAccount = { accountSheet = true },
        onOpenProfile = { navController.navigate(Routes.PROFILE) },
        onOpenCommand = { navController.navigate(Routes.command()) },
        onReconnect = { navController.navigate(Routes.LOGIN) },
        home = {
            HomeContent(
                home, pins, recents,
                onNavigate = { navController.navigate(it) },
                onOpenSaved = { a -> navController.navigate(Routes.action(container.actionStore.put(a.toDraft()))) }
            )
        },
        browse = { ResourcesContent(resources) { navController.navigate(it) } },
        activity = {
            ActivityContent(
                activity,
                onRepeat = { h -> navController.navigate(Routes.explorer(method = h.method, path = h.path, query = h.query)) },
                onOpenTransfers = { navController.navigate(Routes.TRANSFERS) },
                onOpenAuditLogs = ctx.account?.let { a -> { navController.navigate(Routes.auditLogs(a.id)) } }
            )
        }
    )
    if (accountSheet) {
        PickerSheet(
            title = "Account",
            options = homeUi.accounts.map { NamedRef(it.id, it.name) },
            selectedId = ctx.account?.id,
            allowNone = false,
            onPick = { picked -> accountSheet = false; homeUi.accounts.firstOrNull { it.id == picked?.id }?.let(home::selectAccount) },
            onDismiss = { accountSheet = false }
        )
    }
}

/** Hands a draft to the action screen. */
fun NavHostController.openDraft(container: AppContainer, draft: ActionDraft) =
    navigate(Routes.action(container.actionStore.put(draft)))

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
        val workingContext by container.contextStore.state.collectAsState()
        ApiExplorerScreen(
            viewModel = vm,
            profileLabel = profileLabel(),
            accountLabel = workingContext.account?.name,
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
        ApiCatalogScreen(vm, onBack = back, onOpen = { id -> navController.navigate(Routes.action(endpointId = id)) })
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
        val workingContext by container.contextStore.state.collectAsState()
        R2ObjectsScreen(
            vm, profileLabel(), workingContext.account?.takeIf { it.id == accountId }?.name ?: accountId,
            onBack = back,
            onOpenSettings = { navController.navigate(Routes.r2Bucket(accountId, bucket)) },
            onOpenTransfers = { navController.navigate(Routes.TRANSFERS) }
        )
    }

    composable(Routes.COMMAND, arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) { entry ->
        val initial = entry.arguments?.getString("q")?.let(Routes::decodeArg).orEmpty()
        val vm = viewModel<CommandViewModel>(factory = factoryOf {
            CommandViewModel(
                engineProvider = { container.commandEngine() },
                planner = container.aiPlanner,
                contextFlow = container.contextStore.state,
                pinsFlow = container.actionStore.pins,
                recentsFlow = container.actionStore.recents,
                loadZones = { account ->
                    (container.zonesRepository.listZones(accountId = account, maxPages = 4) as? ApiResult.Success)?.data.orEmpty()
                        .map { NamedRef(it.id, it.name, it.account?.id) }
                },
                loadAccounts = { (container.accountsRepository.listAccounts() as? ApiResult.Success)?.data.orEmpty().map { NamedRef(it.id, it.name) } },
                loadResources = { account -> container.resourceIndex.resources(account) },
                selectAccount = container.contextStore::selectAccount,
                selectZone = container.contextStore::selectZone,
                initialQuery = initial
            )
        })
        CommandScreen(
            vm,
            onClose = back,
            onOpenDraft = { d -> navController.popBackStack(); navController.openDraft(container, d) },
            onNavigate = { r -> navController.popBackStack(); navController.navigate(r) }
        )
    }

    composable(
        Routes.ACTION,
        arguments = listOf("draft", "endpoint").map { n -> navArgument(n) { type = NavType.StringType; defaultValue = "" } }
    ) { entry ->
        val draftId = entry.arguments?.getString("draft")?.let(Routes::decodeArg)?.takeIf { it.isNotBlank() }
        val endpointId = entry.arguments?.getString("endpoint")?.let(Routes::decodeArg)?.takeIf { it.isNotBlank() }
        val draft = draftId?.let(container.actionStore::get)
        val vm = viewModel<ActionViewModel>(factory = factoryOf {
            ActionViewModel(
                registryProvider = { container.endpointRegistry() },
                client = container.rawApiClient,
                tokenState = { e, account, zone ->
                    container.capabilityRepository.state.value.capabilities?.evaluate(e, accountId = account, zoneId = zone, zoneAccountId = account)
                        ?: dev.cfmobile.app.core.capabilities.CapabilityState.UNKNOWN
                },
                workingContext = { container.contextStore.state.value },
                zones = {
                    val ctxNow = container.contextStore.state.value
                    (container.zonesRepository.listZones(accountId = ctxNow.account?.id, maxPages = 4) as? ApiResult.Success)?.data.orEmpty()
                        .map { NamedRef(it.id, it.name, it.account?.id) }
                },
                accounts = { (container.accountsRepository.listAccounts() as? ApiResult.Success)?.data.orEmpty().map { NamedRef(it.id, it.name) } },
                onRan = container.actionStore::recordRun,
                isPinned = container.actionStore::isPinned,
                togglePin = container.actionStore::togglePin,
                draft = draft,
                endpointId = endpointId
            )
        })
        val wc by container.contextStore.state.collectAsState()
        val label = listOfNotNull(wc.zone?.name, wc.account?.name).joinToString(" · ").ifBlank { null }
        ActionScreen(
            vm,
            contextLabel = label,
            onBack = back,
            onOpenDraft = { d -> navController.openDraft(container, d) },
            onOpenExplorer = { m, p, q -> navController.navigate(Routes.explorer(method = m, path = p, query = q)) }
        )
    }

    composable(Routes.PROFILE) {
        val wc by container.contextStore.state.collectAsState()
        val activeId by container.accountStore.activeIdFlow.collectAsState()
        var schemaRevision by remember { mutableStateOf<String?>(null) }
        androidx.compose.runtime.LaunchedEffect(Unit) { schemaRevision = container.endpointRegistry().schemaRevision.take(12) }
        ProfileScreen(
            accountId = wc.account?.id,
            profile = container.authRepository.activeAccount?.takeIf { it.id == activeId } ?: container.authRepository.activeAccount,
            profiles = container.authRepository.savedAccounts,
            schemaRevision = schemaRevision,
            onSwitchProfile = { id -> container.authRepository.switchTo(id); back() },
            onAddProfile = { navController.navigate(Routes.LOGIN) },
            onBack = back,
            onNavigate = { navController.navigate(it) }
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
