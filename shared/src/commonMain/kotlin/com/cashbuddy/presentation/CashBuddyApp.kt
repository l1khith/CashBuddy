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
import com.cashbuddy.presentation.accounts.AccountsScreen
import com.cashbuddy.presentation.accounts.AccountsViewModel
import com.cashbuddy.presentation.addtransaction.AddTransactionScreen
import com.cashbuddy.presentation.addtransaction.AddTransactionViewModel
import com.cashbuddy.presentation.budget.BudgetScreen
import com.cashbuddy.presentation.budget.BudgetViewModel
import com.cashbuddy.presentation.components.CashBuddyBottomBar
import com.cashbuddy.presentation.goals.GoalsScreen
import com.cashbuddy.presentation.goals.GoalsViewModel
import com.cashbuddy.presentation.home.HomeScreen
import com.cashbuddy.presentation.home.HomeViewModel
import com.cashbuddy.presentation.navigation.ScreenRoute
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
    modifier: Modifier = Modifier
) {
    CashBuddyTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val homeViewModel: HomeViewModel = koinViewModel()
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

        val topLevelRoutes = listOf("Home", "Transactions", "Stats", "Settings")

        // Only show bottom navigation on top-level screens
        val isTopLevelDestination = currentDestination?.route?.let { routeStr ->
            topLevelRoutes.any { routeStr.contains(it, ignoreCase = true) }
        } ?: true

        val startDestination = when {
            initialRoute?.contains("review", ignoreCase = true) == true -> ScreenRoute.ReviewInbox
            initialRoute?.contains("transact", ignoreCase = true) == true -> ScreenRoute.Transactions
            initialRoute?.contains("stat", ignoreCase = true) == true -> ScreenRoute.Stats
            initialRoute?.contains("setting", ignoreCase = true) == true -> ScreenRoute.Settings
            else -> ScreenRoute.Home
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
                        unreviewedCount = homeState.unreviewedCount,
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
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<ScreenRoute.Home> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToReview = { navController.navigate(ScreenRoute.ReviewInbox) },
                        onNavigateToDetail = { id -> navController.navigate(ScreenRoute.TransactionDetail(id)) },
                        onNavigateToAddTransaction = { navController.navigate(ScreenRoute.AddTransaction()) },
                        onNavigateToAllTransactions = { navController.navigate(ScreenRoute.Transactions) }
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

                composable<ScreenRoute.Accounts> {
                    val accountsViewModel: AccountsViewModel = koinViewModel()
                    AccountsScreen(viewModel = accountsViewModel)
                }

                composable<ScreenRoute.Budgets> {
                    val budgetViewModel: BudgetViewModel = koinViewModel()
                    BudgetScreen(viewModel = budgetViewModel)
                }

                composable<ScreenRoute.Goals> {
                    val goalsViewModel: GoalsViewModel = koinViewModel()
                    GoalsScreen(viewModel = goalsViewModel)
                }

                composable<ScreenRoute.Settings> {
                    val settingsViewModel: SettingsViewModel = koinViewModel()
                    SettingsScreen(viewModel = settingsViewModel)
                }

                composable<ScreenRoute.AddTransaction> {
                    val addViewModel: AddTransactionViewModel = koinViewModel()
                    AddTransactionScreen(
                        viewModel = addViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
