// NO-NETWORK
package com.cashbuddy.data.repository

import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.repository.DataResetStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atomic clear-all write cluster (audit fix P1-C).
 *
 * The four wipe steps previously ran as separate transactions from the ViewModel:
 * transactions → training data → learned tokens → per-account balance zeroing.
 * A crash between steps left the app in a mixed state (wiped transactions with
 * surviving balances/training vocabulary). Everything now commits in ONE
 * driver-level transaction.
 *
 * IMPORTANT: the transaction body must stay free of suspend calls and dispatcher
 * hops — SQLDelight transactions are thread-bound; queries here run directly and
 * synchronously.
 */
class SqlDelightDataResetStore(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : DataResetStore {

    override suspend fun clearAllData(): Int = withContext(dispatcher) {
        database.transactionWithResult {
            database.transactionsQueries.deleteAll()
            database.training_dataQueries.clearRaw()
            database.training_dataQueries.clearCorrections()
            database.learned_tokenQueries.deleteAll()

            // Zero every account balance in the same commit.
            val accounts = database.accountsQueries.getAll().executeAsList()
            for (account in accounts) {
                database.accountsQueries.updateBalance(
                    balance = 0.0,
                    updated_at = com.cashbuddy.platform.currentTimeMillis(),
                    id = account.id
                )
            }
            accounts.size
        }
    }
}
