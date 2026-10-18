package com.cashbuddy.di

import com.cashbuddy.core.CategoryEngine
import com.cashbuddy.core.DuplicateReconciler
import com.cashbuddy.core.ScreenshotParserEngine
import com.cashbuddy.data.repository.AccountRepositoryImpl
import com.cashbuddy.data.repository.SqlDelightBudgetRepository
import com.cashbuddy.data.repository.CategoryRepositoryImpl
import com.cashbuddy.data.repository.DebugLogRepositoryImpl
import com.cashbuddy.data.repository.LearnedTokenRepositoryImpl
import com.cashbuddy.data.repository.MerchantRuleRepositoryImpl
import com.cashbuddy.data.repository.RawMessageRepositoryImpl
import com.cashbuddy.data.repository.SettingsRepositoryImpl
import com.cashbuddy.data.repository.SignalObservationRepositoryImpl
import com.cashbuddy.data.repository.TrainingDataRepositoryImpl
import com.cashbuddy.data.repository.TransactionRepositoryImpl
import com.cashbuddy.debug.DebugConfig
import com.cashbuddy.debug.DebugLogger
import com.cashbuddy.domain.repository.AccountRepository
import com.cashbuddy.domain.repository.BudgetRepository
import com.cashbuddy.domain.repository.CategoryRepository
import com.cashbuddy.domain.repository.DebugLogRepository
import com.cashbuddy.domain.repository.LearnedTokenRepository
import com.cashbuddy.presentation.debug.DebugLogViewModel
import com.cashbuddy.domain.repository.MerchantRuleRepository
import com.cashbuddy.domain.repository.RawMessageRepository
import com.cashbuddy.domain.repository.SettingsRepository
import com.cashbuddy.domain.repository.SignalObservationRepository
import com.cashbuddy.domain.repository.TrainingDataRepository
import com.cashbuddy.domain.repository.TransactionRepository
import com.cashbuddy.domain.usecase.BatchCategorizeUseCase
import com.cashbuddy.domain.usecase.CalculateBalanceUseCase
import com.cashbuddy.domain.usecase.ConfirmTransactionUseCase
import com.cashbuddy.domain.usecase.ExportDataUseCase
import com.cashbuddy.domain.usecase.ImportDataUseCase
import com.cashbuddy.domain.usecase.GetDateRangeSummaryUseCase
import com.cashbuddy.domain.usecase.GetRecentTransactionsUseCase
import com.cashbuddy.domain.usecase.GetTransactionsByDateRangeWithLimitUseCase
import com.cashbuddy.domain.usecase.GetUnreviewedCountUseCase
import com.cashbuddy.domain.usecase.ManualAddTransactionUseCase
import com.cashbuddy.domain.usecase.ModifyTransactionUseCase
import com.cashbuddy.domain.usecase.RejectTransactionUseCase
import com.cashbuddy.presentation.review.ReviewReducer
import com.cashbuddy.presentation.addtransaction.AddTransactionViewModel
import com.cashbuddy.presentation.budget.BudgetViewModel
import com.cashbuddy.presentation.home.HomeViewModel
import com.cashbuddy.presentation.personalization.PersonalizationViewModel
import com.cashbuddy.presentation.review.ReviewViewModel
import com.cashbuddy.presentation.settings.SettingsViewModel
import com.cashbuddy.presentation.stats.StatsViewModel
import com.cashbuddy.presentation.transactions.TransactionDetailViewModel
import com.cashbuddy.presentation.transactions.TransactionListViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Coroutine Dispatcher for Repositories
    single<CoroutineDispatcher> { Dispatchers.Default }

    // Pure Kotlin Core Engines (Multiplatform)
    single { CategoryEngine() }
    single { ScreenshotParserEngine(get()) }
    single { com.cashbuddy.core.prob.SourceDetector() }
    single { com.cashbuddy.core.prob.RecentStateRepository() }
    single { com.cashbuddy.core.prob.TokenEvidenceExtractor(get()) }
    single { com.cashbuddy.core.prob.EvidenceExtractor(recentStateRepository = get(), tokenEvidenceExtractor = get()) }
    single { com.cashbuddy.core.prob.FieldConfidenceEstimator() }
    single { com.cashbuddy.core.prob.PolicyEngine() }
    single { com.cashbuddy.core.prob.DedupEngine }
    single { DuplicateReconciler(get(), get()) }
    single { com.cashbuddy.core.prob.AccountRegistry(get()) }
    single<com.cashbuddy.domain.repository.RecurringPatternRepository> { com.cashbuddy.data.repository.RecurringPatternRepositoryImpl(db = get(), dispatcher = get()) }
    single { com.cashbuddy.core.budget.BudgetEngine(transactionRepository = get(), recurringPatternRepository = getOrNull()) }
    single { com.cashbuddy.core.budget.BudgetAlertScheduler(get(), get(), getOrNull()) }
    singleOf(::SignalObservationRepositoryImpl) bind SignalObservationRepository::class
    singleOf(::RawMessageRepositoryImpl) bind RawMessageRepository::class
    single<DebugLogRepository> { DebugLogRepositoryImpl(database = get(), dispatcher = get()) }
    single { DebugConfig(isDebugBuild = false, settingsRepository = get()) }
    single { DebugLogger(repository = get(), config = get()) }
    single<com.cashbuddy.core.prob.Calibrator> { com.cashbuddy.core.prob.LikelihoodCalibrator(get()) }
    single { com.cashbuddy.core.prob.ProbabilisticClassifier(get(), get(), get()) }
    single {
        com.cashbuddy.core.prob.MessagePipeline(
            sourceDetector = get(),
            evidenceExtractor = get(),
            classifier = get(),
            policy = get(),
            dedup = get(),
            accountRegistry = get(),
            categoryEngine = get(),
            transactionRepo = get(),
            rawMessageRepo = get(),
            categoryRepo = get(),
            debugLogger = get(),
            recentStateRepository = get(),
            settingsRepo = get(),
            budgetAlertScheduler = getOrNull(),
            recurringPatternRepo = getOrNull()
        )
    }

    // Repositories
    singleOf(::TransactionRepositoryImpl) bind TransactionRepository::class
    singleOf(::AccountRepositoryImpl) bind AccountRepository::class
    singleOf(::CategoryRepositoryImpl) bind CategoryRepository::class
    singleOf(::SqlDelightBudgetRepository) bind BudgetRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class
    singleOf(::MerchantRuleRepositoryImpl) bind MerchantRuleRepository::class
    singleOf(::TrainingDataRepositoryImpl) bind TrainingDataRepository::class
    singleOf(::LearnedTokenRepositoryImpl) bind LearnedTokenRepository::class

    // Use Cases
    factoryOf(::CalculateBalanceUseCase)
    factoryOf(::GetRecentTransactionsUseCase)
    factoryOf(::GetTransactionsByDateRangeWithLimitUseCase)
    factoryOf(::GetDateRangeSummaryUseCase)
    factoryOf(::GetUnreviewedCountUseCase)
    factoryOf(::ManualAddTransactionUseCase)
    factoryOf(::ConfirmTransactionUseCase)
    factoryOf(::RejectTransactionUseCase)
    factoryOf(::ModifyTransactionUseCase)
    factoryOf(::ExportDataUseCase)
    factoryOf(::ImportDataUseCase)
    factoryOf(::ReviewReducer)
    factoryOf(::BatchCategorizeUseCase)

    // ViewModels
    viewModelOf(::HomeViewModel)
    viewModelOf(::ReviewViewModel)
    viewModelOf(::TransactionListViewModel)
    viewModel { (id: Long) -> TransactionDetailViewModel(id, get(), get(), get(), get(), get()) }
    viewModelOf(::StatsViewModel)
    viewModel {
        BudgetViewModel(
            budgetRepository = get(),
            categoryRepository = get(),
            budgetEngine = get()
        )
    }
    viewModel { (category: String, period: String) ->
        com.cashbuddy.presentation.budget.BudgetDrilldownViewModel(
            category = category,
            periodName = period,
            transactionRepository = get(),
            budgetEngine = get()
        )
    }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), getOrNull(), getOrNull(), getOrNull(), getOrNull(), getOrNull()) }
    viewModel { PersonalizationViewModel(get(), get(), get(), get(), getOrNull()) }
    viewModelOf(::AddTransactionViewModel)
    viewModel { DebugLogViewModel(get(), get(), getOrNull()) }
}
