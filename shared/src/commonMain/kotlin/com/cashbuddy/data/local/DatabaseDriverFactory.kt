package com.cashbuddy.data.local

import app.cash.sqldelight.db.SqlDriver
import com.cashbuddy.db.AppDatabase

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driver: SqlDriver): AppDatabase {
    return AppDatabase(driver)
}
