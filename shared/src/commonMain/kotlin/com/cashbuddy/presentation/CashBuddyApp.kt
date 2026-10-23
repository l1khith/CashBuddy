package com.cashbuddy.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
import com.cashbuddy.presentation.theme.CashBuddyTheme
import com.cashbuddy.presentation.transactions.TransactionDetailScreen
import com.cashbuddy.presentation.transactions.TransactionDetailViewModel
import com.cashbuddy.presentation.transactions.TransactionListScreen
import com.cashbuddy.presentation.transactions.TransactionListViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CashBuddyApp(
    initialRoute: String? = null,
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CashBuddyTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val homeViewModel: HomeViewModel = koinViewModel()
        val unreviewedCount by homeViewModel.unreviewedCount.collectAsStateWithLifecycle()

        val topLevelRoutes = listOf("Home", "Transactions", "Stats", "Settings")

        // Only show bottom navigation on top-level screens
        val isTopLevelDestination = currentDestination?.route?.let { routeStr ->
            topLevelRoutes.any { routeStr.contains(it, ignoreCase = true) }
        } ?: true

        val startDestination = when {
            initialRoute?.contains("add", ignoreCase = true) == true -> ScreenRoute.AddTransaction()
            initialRoute?.contains("review", ignoreCase = true) == true -> ScreenRoute.ReviewInbox
            initialRoute?.contains("transact", ignoreCase = true) == true -> ScreenRoute.Transactions
            initialRoute?.contains("screenshot", ignoreCase = true) == true -> ScreenRoute.Transactions
            initialRoute?.contains("stat", ignoreCase = true) == true -> ScreenRoute.Stats
            initialRoute?.contains("setting", ignoreCase = true) == true -> ScreenRoute.Settings
            initialRoute?.contains("budget", ignoreCase = true) == true -> ScreenRoute.Budgets
            else -> ScreenRoute.Home
        }

        androidx.compose.runtime.LaunchedEffect(initialRoute) {
            if (!initialRoute.isNullOrBlank()) {
                when {
                    initialRoute.contains("add", ignoreCase = true) -> navController.navigate(ScreenRoute.AddTransaction()) { launchSingleTop = true }
                    initialRoute.contains("review", ignoreCase = true) -> navController.navigate(ScreenRoute.ReviewInbox) { launchSingleTop = true }
                    initialRoute.contains("transact", ignoreCase = true) || initialRoute.contains("screenshot", ignoreCase = true) -> navController.navigate(ScreenRoute.Transactions) { launchSingleTop = true }
                    initialRoute.contains("stat", ignoreCase = true) -> navController.navigate(ScreenRoute.Stats) { launchSingleTop = true }
                    initialRoute.contains("setting", ignoreCase = true) -> navController.navigate(ScreenRoute.Settings) { launchSingleTop = true }
                    initialRoute.contains("budget", ignoreCase = true) -> navController.navigate(ScreenRoute.Budgets) { launchSingleTop = true }
                }
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (isTopLevelDestination) {
                    val currentRouteStr = currentDestination?.route ?: ""
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
                            navController.navigate(ScreenRoute.AddTransaction())
                        },
                        unreviewedCount = unreviewedCount,
                        onNavigateToReview = {
                            navController.navigate(ScreenRoute.ReviewInbox)
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                composable<ScreenRoute.Home> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToReview = { navController.navigate(ScreenRoute.ReviewInbox) },
                        onNavigateToDetail = { id -> navController.navigate(ScreenRoute.TransactionDetail(id)) },
                        onNavigateToAddTransaction = { navController.navigate(ScreenRoute.AddTransaction()) },
                        onNavigateToAllTransactions = { navController.navigate(ScreenRoute.Transactions) },
                        onNavigateToBudgets = { navController.navigate(ScreenRoute.Budgets) }
                    )
                }

                composable<ScreenRoute.ReviewInbox> {
                    val reviewViewModel: ReviewViewModel = koinViewModel()
                    ReviewScreen(
                        viewModel = reviewViewModel,
                        onNavigateBack = {
                            val popped = navController.popBackStack()
                            if (!popped) {
                                navController.navigate(ScreenRoute.Home) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable<ScreenRoute.Transactions> {
                    val listViewModel: TransactionListViewModel = koinViewModel()
                    TransactionListScreen(
                        viewModel = listViewModel,
                        onNavigateToDetail = { id -> navController.navigate(ScreenRoute.TransactionDetail(id)) }
                    )
                }

                composable<ScreenRoute.TransactionDetail> { backStackEntry ->
                    val detailRoute: ScreenRoute.TransactionDetail = backStackEntry.toRoute()
                    val detailViewModel: TransactionDetailViewModel = koinViewModel(
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
                            )
                        }
                    )
                }

                composable<ScreenRoute.BudgetDrilldown> { backStackEntry ->
                    val route: ScreenRoute.BudgetDrilldown = backStackEntry.toRoute()
                    val drilldownViewModel: BudgetDrilldownViewModel = koinViewModel(
                        parameters = { parametersOf(route.category, route.period) }
                    )
                    BudgetDrilldownScreen(
                        viewModel = drilldownViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onTransactionClick = { txId -> navController.navigate(ScreenRoute.TransactionDetail(txId)) }
                    )
                }

                composable<ScreenRoute.Settings> {
                    val settingsViewModel: SettingsViewModel = koinViewModel()
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToPersonalization = { navController.navigate(ScreenRoute.PersonalizationDashboard) },
                        onNavigateToDebugLog = { navController.navigate(ScreenRoute.DebugLog) },
                        onOpenNotificationSettings = onOpenNotificationSettings
                    )
                }

                composable<ScreenRoute.AddTransaction> { backStackEntry ->
                    val route: ScreenRoute.AddTransaction = backStackEntry.toRoute()
                    val addViewModel: AddTransactionViewModel = koinViewModel()
                    AddTransactionScreen(
                        viewModel = addViewModel,
                        prefilledId = route.prefilledId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable<ScreenRoute.PersonalizationDashboard> {
                    val personalizationViewModel: PersonalizationViewModel = koinViewModel()
                    PersonalizationDashboardScreen(
                        viewModel = personalizationViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDataView = { navController.navigate(ScreenRoute.DataView) }
                    )
                }

                composable<ScreenRoute.DataView> {
                    val personalizationViewModel: PersonalizationViewModel = koinViewModel()
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
}
