---
name: paisapal-architecture
description: >-
  Use this skill when designing, organizing, or refactoring code in PaisaPal KMP according to Clean Architecture,
  Unidirectional Data Flow (UDF), MVVM / MVI patterns, and Koin 4.0 dependency injection.
---

# PaisaPal Clean Architecture & KMP Design Patterns

## Overview
PaisaPal enforces strict separation of concerns across Presentation, Domain, Data, and Platform layers across Kotlin Multiplatform targets.

## Layer Boundaries & Rules

1. **Domain Layer (`shared/src/commonMain/kotlin/com/paisapal/domain/`)**:
   - **Zero Platform Dependencies**: No Android imports (`android.*`), no SQLDelight imports, no framework libraries. Pure Kotlin only.
   - **Entities**: Immutable data classes (`Transaction`, `Account`, `Category`, `Budget`, `Goal`, `Money`).
   - **Repository Interfaces (Ports)**: Return reactive `kotlinx.coroutines.flow.Flow<T>` or suspend functions.
   - **Use Cases (Interactors)**: Single Responsibility Principle. Each use case executes a single business action (e.g., `CalculateBalanceUseCase`, `ConfirmTransactionUseCase`).

2. **Data Layer (`shared/src/commonMain/kotlin/com/paisapal/data/`)**:
   - **Repository Implementations (Adapters)**: Implement domain interfaces using SQLDelight queries.
   - **Threading Discipline**: All disk, parser, and database operations must run on an injected `CoroutineDispatcher` (defaulting to `Dispatchers.IO`).
   - **Data Mapping**: Convert SQLDelight generated records to pure domain entities before exposing to domain layer.

3. **Presentation Layer (`composeApp/src/commonMain/kotlin/com/paisapal/presentation/`)**:
   - **MVVM Default**: For standard dashboard and listing screens (`Home`, `Transactions`, `Stats`, `Accounts`, `Settings`), use `ViewModel` with `StateFlow<UiState>` and `Channel<UiEffect>`.
   - **MVI Pattern**: For complex triage states (`Review Inbox`, `Budget Setup`), use a unidirectional intent-state-reducer architecture with a pure `Reducer` function.
   - **Immutable State**: State objects are always read-only `data class` models modified via `.copy()`.

4. **Koin 4.0 Dependency Injection**:
   - Register all singletons with `single { ... }`.
   - Register use cases with `factory { ... }`.
   - Register viewmodels with `viewModelOf(::XViewModel)`.
   - Never use reflection or code generation plugins.

## Verification Checklist
- [ ] No `android.*` imports exist in `domain/`.
- [ ] Use cases are injected with repository interfaces, not concrete implementations.
- [ ] All database queries are exposed through `Flow` streams mapped to domain models.
- [ ] ViewModels expose immutable `StateFlow` and read-only `Flow` for effects.
