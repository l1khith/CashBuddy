package com.cashbuddy.di

import com.cashbuddy.data.repository.AccountRepositoryImpl
import com.cashbuddy.data.repository.BudgetRepositoryImpl
import com.cashbuddy.data.repository.CategoryRepositoryImpl
import com.cashbuddy.data.repository.GoalRepositoryImpl
import com.cashbuddy.data.repository.SettingsRepositoryImpl
import com.cashbuddy.data.repository.TransactionRepositoryImpl
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.GoalRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.CalculateBalanceUseCase
import com.cashbuddy.domain.usecase.ConfirmTransactionUseCase
import com.cashbuddy.domain.usecase.ExportDataUseCase
import com.cashbuddy.domain.usecase.GenerateCategoryBreakdownUseCase
import com.cashbuddy.domain.usecase.GetMonthlySummaryUseCase
import com.cashbuddy.domain.usecase.GetRecentTransactionsUseCase
import com.cashbuddy.domain.usecase.GetUnreviewedCountUseCase
import com.cashbuddy.domain.usecase.ManualAddTransactionUseCase
import com.cashbuddy.domain.usecase.ModifyTransactionUseCase
import com.cashbuddy.domain.usecase.RejectTransactionUseCase
import com.cashbuddy.presentation.accounts.AccountsViewModel
import com.cashbuddy.presentation.addtransaction.AddTransactionViewModel
import com.cashbuddy.presentation.budget.BudgetViewModel
import com.cashbuddy.presentation.goals.GoalsViewModel
import com.cashbuddy.presentation.home.HomeViewModel
import com.cashbuddy.presentation.review.ReviewReducer
import com.cashbuddy.presentation.review.ReviewViewModel
import com.cashbuddy.presentation.settings.SettingsViewModel
import com.cashbuddy.presentation.stats.StatsViewModel
import com.cashbuddy.presentation.transactions.TransactionDetailViewModel
import com.cashbuddy.presentation.transactions.TransactionListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Repositories
    singleOf(::TransactionRepositoryImpl) bind TransactionRepository::class
    singleOf(::AccountRepositoryImpl) bind AccountRepository::class
    singleOf(::CategoryRepositoryImpl) bind CategoryRepository::class
    singleOf(::BudgetRepositoryImpl) bind BudgetRepository::class
    singleOf(::GoalRepositoryImpl) bind GoalRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class

    // Use Cases
    factoryOf(::CalculateBalanceUseCase)
    factoryOf(::GetRecentTransactionsUseCase)
    factoryOf(::GetUnreviewedCountUseCase)
    factoryOf(::GetMonthlySummaryUseCase)
    factoryOf(::GenerateCategoryBreakdownUseCase)
    factoryOf(::ManualAddTransactionUseCase)
    factoryOf(::ConfirmTransactionUseCase)
    factoryOf(::RejectTransactionUseCase)
    factoryOf(::ModifyTransactionUseCase)
    factoryOf(::ExportDataUseCase)
    factoryOf(::ReviewReducer)

    // ViewModels
    viewModelOf(::HomeViewModel)
    viewModelOf(::ReviewViewModel)
    viewModelOf(::TransactionListViewModel)
    viewModel { (id: Long) -> TransactionDetailViewModel(id, get(), get()) }
    viewModelOf(::StatsViewModel)
    viewModelOf(::AccountsViewModel)
    viewModelOf(::BudgetViewModel)
    viewModelOf(::GoalsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AddTransactionViewModel)
}
