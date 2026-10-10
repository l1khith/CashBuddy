# Tasks: Mathematical Formulas Integration (specs/math-integration/tasks.md)

- [x] **T1 — JaroWinkler implementation**
  - **Files**:
    - Create `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/JaroWinkler.kt`
    - Create `shared/src/commonTest/kotlin/com/cashbuddy/core/prob/JaroWinklerTest.kt`
  - **Done when**:
    - All seven `JaroWinklerTest` cases pass with exact oracle tolerances.

- [x] **T2 — MerchantMap fuzzy tier**
  - **Files**:
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MerchantMap.kt`
    - Create `shared/src/commonTest/kotlin/com/cashbuddy/core/prob/MerchantFuzzyTest.kt`
  - **Done when**:
    - All six `MerchantFuzzyTest` cases pass.
    - `AccuracyHarnessTest` precision remains 100%.

- [ ] **T3 — Threshold sweep**
  - **Files**:
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MerchantMap.kt` (threshold sweep evaluation)
    - Modify `docs/accuracy/accuracy_report.md` (add sweep table)
  - **Done when**:
    - Sweep table recorded for thresholds 0.75 to 0.95.
    - Final threshold selected and hardcoded.
    - Precision regression = 0.

- [ ] **T4 — FraudDetector MAD implementation**
  - **Files**:
    - Modify `shared/src/commonMain/sqldelight/com/cashbuddy/db/transactions.sq` (add `getAmountsAtMerchant`)
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/domain/repository/TransactionRepository.kt`
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionRepositoryImpl.kt`
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt`
    - Create `shared/src/commonTest/kotlin/com/cashbuddy/core/FraudDetectorTest.kt`
  - **Done when**:
    - All four `FraudDetectorTest` cases pass.

- [ ] **T5 — MessagePipeline integration**
  - **Files**:
    - Modify `shared/src/commonMain/sqldelight/com/cashbuddy/db/transactions.sq` (add `updateNotes`)
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/domain/repository/TransactionRepository.kt` (add `updateNotes`)
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionRepositoryImpl.kt` (add `updateNotes`)
    - Modify `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt`
  - **Done when**:
    - Anomaly is written to transaction notes when `isAnomalous = true`.
    - No behavior change when `isAnomalous = false`.

- [ ] **T6 — Documentation**
  - **Files**:
    - Modify `docs/ARCHITECTURE_HLD.md`
    - Modify `specs/README.md`
  - **Done when**:
    - The three formulas are documented with their respective architectural roles.
    - `specs/math-integration/` is listed in the specifications index.

- [ ] **T7 — Final verification**
  - **Done when**:
    - `./gradlew :shared:testAndroidHostTest` passes.
    - `./gradlew :androidApp:assembleRelease` succeeds.
    - Release APK size delta $\le 100\text{ KB}$.
    - Zero forbidden files created or modified.
