package com.cashbuddy.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.cashbuddy.domain.usecase.GetUnreviewedCountUseCase
import com.cashbuddy.presentation.addtransaction.AddTransactionScreen
import com.cashbuddy.presentation.addtransaction.AddTransactionViewModel
import com.cashbuddy.presentation.budget.BudgetDrilldownScreen
import com.cashbuddy.presentation.budget.BudgetDrilldownViewModel
import com.cashbuddy.presentation.budget.BudgetListScreen
import com.cashbuddy.presentation.budget.BudgetViewModel
import com.cashbuddy.presentation.components.CashBuddyBottomBar
import com.cashbuddy.presentation.home.HomeScreen
import com.cashbuddy.presentation.home.HomeViewModel
import com.cashbuddy.presentation.navigation.ScreenRoute
import com.cashbuddy.presentation.personalization.DataViewScreen
import com.cashbuddy.presentation.personalization.PersonalizationDashboardScreen
import com.cashbuddy.presentation.personalization.PersonalizationViewModel
import com.cashbuddy.presentation.review.ReviewScreen
import com.cashbuddy.presentation.review.ReviewViewModel
import com.cashbuddy.presentation.settings.SettingsScreen
import com.cashbuddy.presentation.settings.SettingsViewModel
import com.cashbuddy.presentation.stats.StatsScreen
import com.cashbuddy.presentation.stats.StatsViewModel
import com.cashbuddy.presentation.transactions.TransactionDetailScreen
import com.cashbuddy.presentation.transactions.TransactionDetailViewModel
import com.cashbuddy.presentation.transactions.TransactionListScreen
import com.cashbuddy.presentation.transactions.TransactionListViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CashBuddyApp(
    initialRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
    clipboardUtr: String? = null,
    onConsumeClipboardUtr: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val getUnreviewedCountUseCase: GetUnreviewedCountUseCase = koinInject()
    val unreviewedCount by getUnreviewedCountUseCase().collectAsStateWithLifecycle(0)

    val isTopLevelDestination = currentDestination != null && (
        currentDestination.hasRoute(ScreenRoute.Home::class) ||
        currentDestination.hasRoute(ScreenRoute.Transactions::class) ||
        currentDestination.hasRoute(ScreenRoute.Stats::class) ||
        currentDestination.hasRoute(ScreenRoute.Settings::class)
    )

    val currentRouteStr = when {
        currentDestination?.hasRoute(ScreenRoute.Home::class) == true -> "Home"
        currentDestination?.hasRoute(ScreenRoute.Transactions::class) == true -> "Transactions"
        currentDestination?.hasRoute(ScreenRoute.Stats::class) == true -> "Stats"
        currentDestination?.hasRoute(ScreenRoute.Settings::class) == true -> "Settings"
        else -> ""
    }

    LaunchedEffect(initialRoute) {
        if (!initialRoute.isNullOrBlank()) {
            val target = when (initialRoute.trim().lowercase()) {
                "add", "add_transaction" -> ScreenRoute.AddTransaction()
                "review", "review_inbox" -> ScreenRoute.ReviewInbox
                "transact", "transactions", "screenshot", "processing_screenshot" -> ScreenRoute.Transactions
                "stat", "stats" -> ScreenRoute.Stats
                "setting", "settings" -> ScreenRoute.Settings
                "budget", "budgets" -> ScreenRoute.Budgets
                else -> null
            }
            onRouteConsumed()
            if (target != null) {
                navController.navigate(target) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(clipboardUtr) {
        if (!clipboardUtr.isNullOrBlank()) {
            val utr = clipboardUtr
            onConsumeClipboardUtr()
            val result = snackbarHostState.showSnackbar(
                message = "UPI Ref $utr detected.",
                actionLabel = "Log it",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                navController.navigate(ScreenRoute.AddTransaction(prefilledUtr = utr)) {
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelDestination) {
                CashBuddyBottomBar(
                    currentRoute = currentRouteStr,
                    onNavigateToHome = {
                        navController.navigate(ScreenRoute.Home) {
                            popUpTo(ScreenRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTransactions = {
                        navController.navigate(ScreenRoute.Transactions) {
                            popUpTo(ScreenRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToStats = {
                        navController.navigate(ScreenRoute.Stats) {
                            popUpTo(ScreenRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(ScreenRoute.Settings) {
                            popUpTo(ScreenRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddClick = {
                        navController.navigate(ScreenRoute.AddTransaction()) {
                            launchSingleTop = true
                        }
                    },
                    unreviewedCount = unreviewedCount,
                    onNavigateToReview = {
                        navController.navigate(ScreenRoute.ReviewInbox) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScreenRoute.Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable<ScreenRoute.Home> {
                val homeViewModel: HomeViewModel = koinViewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToReview = {
                        navController.navigate(ScreenRoute.ReviewInbox) { launchSingleTop = true }
                    },
                    onNavigateToDetail = { id ->
                        navController.navigate(ScreenRoute.TransactionDetail(id)) { launchSingleTop = true }
                    },
                    onNavigateToAddTransaction = {
                        navController.navigate(ScreenRoute.AddTransaction()) { launchSingleTop = true }
                    },
                    onNavigateToAllTransactions = {
                        navController.navigate(ScreenRoute.Transactions) {
                            popUpTo(ScreenRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToBudgets = {
                        navController.navigate(ScreenRoute.Budgets) { launchSingleTop = true }
                    }
                )
            }

            composable<ScreenRoute.ReviewInbox> {
                val reviewViewModel: ReviewViewModel = koinViewModel()
                ReviewScreen(
                    viewModel = reviewViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<ScreenRoute.Transactions> {
                val listViewModel: TransactionListViewModel = koinViewModel()
                TransactionListScreen(
                    viewModel = listViewModel,
                    onNavigateToDetail = { id ->
                        navController.navigate(ScreenRoute.TransactionDetail(id)) { launchSingleTop = true }
                    }
                )
            }

            composable<ScreenRoute.TransactionDetail> { backStackEntry ->
                val detailRoute: ScreenRoute.TransactionDetail = backStackEntry.toRoute()
                val detailViewModel: TransactionDetailViewModel = koinViewModel(
                    key = "tx_${detailRoute.transactionId}",
                    parameters = { parametersOf(detailRoute.transactionId) }
                )
                TransactionDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<ScreenRoute.Stats> {
                val statsViewModel: StatsViewModel = koinViewModel()
                StatsScreen(viewModel = statsViewModel)
            }

            composable<ScreenRoute.Budgets> {
                val budgetViewModel: BudgetViewModel = koinViewModel()
                BudgetListScreen(
                    viewModel = budgetViewModel,
                    onBudgetClick = { status ->
                        navController.navigate(
                            ScreenRoute.BudgetDrilldown(
                                category = status.budget.category,
                                period = status.budget.period.name
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable<ScreenRoute.BudgetDrilldown> { backStackEntry ->
                val route: ScreenRoute.BudgetDrilldown = backStackEntry.toRoute()
                val drilldownViewModel: BudgetDrilldownViewModel = koinViewModel(
                    key = "budget_${route.category}_${route.period}",
                    parameters = { parametersOf(route.category, route.period) }
                )
                BudgetDrilldownScreen(
                    viewModel = drilldownViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onTransactionClick = { txId ->
                        navController.navigate(ScreenRoute.TransactionDetail(txId)) { launchSingleTop = true }
                    }
                )
            }

            composable<ScreenRoute.Settings> {
                val settingsViewModel: SettingsViewModel = koinViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToPersonalization = {
                        navController.navigate(ScreenRoute.PersonalizationDashboard) { launchSingleTop = true }
                    },
                    onNavigateToDebugLog = {
                        navController.navigate(ScreenRoute.DebugLog) { launchSingleTop = true }
                    },
                    onOpenNotificationSettings = onOpenNotificationSettings
                )
            }

            composable<ScreenRoute.AddTransaction> { backStackEntry ->
                val route: ScreenRoute.AddTransaction = backStackEntry.toRoute()
                val addViewModel: AddTransactionViewModel = koinViewModel()
                AddTransactionScreen(
                    viewModel = addViewModel,
                    prefilledId = route.prefilledId,
                    prefilledUtr = route.prefilledUtr,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<ScreenRoute.PersonalizationDashboard> { backStackEntry ->
                val personalizationViewModel: PersonalizationViewModel = koinViewModel(
                    viewModelStoreOwner = backStackEntry
                )
                PersonalizationDashboardScreen(
                    viewModel = personalizationViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDataView = {
                        navController.navigate(ScreenRoute.DataView) { launchSingleTop = true }
                    }
                )
            }

            composable<ScreenRoute.DataView> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<ScreenRoute.PersonalizationDashboard>()
                }
                val personalizationViewModel: PersonalizationViewModel = koinViewModel(
                    viewModelStoreOwner = parentEntry
                )
                DataViewScreen(
                    viewModel = personalizationViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable<ScreenRoute.DebugLog> {
                val debugLogViewModel: com.cashbuddy.presentation.debug.DebugLogViewModel = koinViewModel()
                com.cashbuddy.presentation.debug.DebugLogScreen(
                    viewModel = debugLogViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
