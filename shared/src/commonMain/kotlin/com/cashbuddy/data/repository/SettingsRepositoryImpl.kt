package com.cashbuddy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.cashbuddy.db.AppDatabase
import com.cashbuddy.domain.model.UserSettings
import com.cashbuddy.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val db: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SettingsRepository {

    private val queries = db.settingsQueries

    override fun getSettings(): Flow<UserSettings> =
        queries.getAll().asFlow().mapToList(dispatcher).map { list ->
            val map = list.associate { it.key to it.value_ }
            UserSettings(
                autoConfirmThreshold = map["autoConfirmThreshold"]?.toDoubleOrNull() ?: 10000.0,
                minConfidenceThreshold = map["minConfidenceThreshold"]?.toFloatOrNull() ?: 0.85f,
                notificationsEnabled = map["notificationsEnabled"]?.toBooleanStrictOrNull() ?: true,
                biometricEnabled = map["biometricEnabled"]?.toBooleanStrictOrNull() ?: false,
                preferredCurrency = map["preferredCurrency"] ?: "INR",
                smartNotificationsEnabled = map["smartNotificationsEnabled"]?.toBooleanStrictOrNull() ?: true,
                customAppName = (map["custom_app_name"] ?: UserSettings.DEFAULT_APP_NAME).take(UserSettings.MAX_APP_NAME_LENGTH),
                customAppSubtitle = (map["custom_app_subtitle"] ?: UserSettings.DEFAULT_APP_SUBTITLE).take(UserSettings.MAX_APP_SUBTITLE_LENGTH),
                showAppLogo = map["show_app_logo"]?.toBooleanStrictOrNull() ?: true,
                navOrder = map["nav_order"]?.takeIf { it.isNotBlank() } ?: UserSettings.DEFAULT_NAV_ORDER,
                showHealthInTopBar = map["show_health_in_top_bar"]?.toBooleanStrictOrNull() ?: false,
                lastSyncTimestamp = map["last_sync_timestamp"]?.toLongOrNull() ?: 0L,
                dailyWrapUpHour = map["daily_wrap_up_hour"]?.toIntOrNull() ?: UserSettings.DEFAULT_WRAP_UP_HOUR,
                dailyWrapUpMinute = map["daily_wrap_up_minute"]?.toIntOrNull() ?: UserSettings.DEFAULT_WRAP_UP_MINUTE
            )
        }

    override suspend fun updateSettings(settings: UserSettings): Unit = withContext(dispatcher) {
        val now = com.cashbuddy.platform.currentTimeMillis()
        db.transaction {
            queries.set("autoConfirmThreshold", settings.autoConfirmThreshold.toString(), now)
            queries.set("minConfidenceThreshold", settings.minConfidenceThreshold.toString(), now)
            queries.set("notificationsEnabled", settings.notificationsEnabled.toString(), now)
            queries.set("biometricEnabled", settings.biometricEnabled.toString(), now)
            queries.set("preferredCurrency", settings.preferredCurrency, now)
            queries.set("smartNotificationsEnabled", settings.smartNotificationsEnabled.toString(), now)
            queries.set("custom_app_name", settings.customAppName, now)
            queries.set("custom_app_subtitle", settings.customAppSubtitle, now)
            queries.set("show_app_logo", settings.showAppLogo.toString(), now)
            queries.set("nav_order", settings.navOrder, now)
            queries.set("show_health_in_top_bar", settings.showHealthInTopBar.toString(), now)
            queries.set("last_sync_timestamp", settings.lastSyncTimestamp.toString(), now)
            queries.set("daily_wrap_up_hour", settings.dailyWrapUpHour.toString(), now)
            queries.set("daily_wrap_up_minute", settings.dailyWrapUpMinute.toString(), now)
        }
    }

    override fun getNotificationEnabled(): Flow<Boolean> =
        queries.get("notificationsEnabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: true
        }

    override suspend fun setNotificationEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        queries.set("notificationsEnabled", enabled.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getAutoConfirmThreshold(): Flow<Double> =
        queries.get("autoConfirmThreshold").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toDoubleOrNull() ?: 10000.0
        }

    override suspend fun setAutoConfirmThreshold(threshold: Double): Unit = withContext(dispatcher) {
        queries.set("autoConfirmThreshold", threshold.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getMinConfidenceThreshold(): Flow<Float> =
        queries.get("minConfidenceThreshold").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toFloatOrNull() ?: 0.85f
        }

    override suspend fun setMinConfidenceThreshold(confidence: Float): Unit = withContext(dispatcher) {
        queries.set("minConfidenceThreshold", confidence.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getBiometricEnabled(): Flow<Boolean> =
        queries.get("biometricEnabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: false
        }

    override suspend fun setBiometricEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        queries.set("biometricEnabled", enabled.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override suspend fun setDeveloperMode(enabled: Boolean): Unit = withContext(dispatcher) {
        // Dual key must flip atomically (audit fix P1-C) — a partial write desyncs
        // developer_mode from debug_log_viewer_enabled.
        val now = com.cashbuddy.platform.currentTimeMillis()
        db.transaction {
            queries.set("developer_mode", enabled.toString(), now)
            queries.set("debug_log_viewer_enabled", enabled.toString(), now)
        }
    }

    override fun getDebugLogEnabled(): Flow<Boolean> =
        queries.get("debug_log_viewer_enabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: false
        }

    override suspend fun setDebugLogEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        // Dual key must flip atomically (audit fix P1-C).
        val now = com.cashbuddy.platform.currentTimeMillis()
        db.transaction {
            queries.set("debug_log_viewer_enabled", enabled.toString(), now)
            queries.set("developer_mode", enabled.toString(), now)
        }
    }

    override suspend fun setPreferredCurrency(currencyCode: String): Unit = withContext(dispatcher) {
        queries.set("preferredCurrency", currencyCode, com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getIgnoredPackages(): Flow<List<String>> =
        queries.getIgnoredPackages().asFlow().mapToList(dispatcher)

    override suspend fun isPackageIgnored(packageName: String): Boolean = withContext(dispatcher) {
        queries.isPackageIgnored(packageName).executeAsOne() > 0L
    }

    override suspend fun addIgnoredPackage(packageName: String): Unit = withContext(dispatcher) {
        queries.addIgnoredPackage(packageName.trim())
    }

    override suspend fun removeIgnoredPackage(packageName: String): Unit = withContext(dispatcher) {
        queries.removeIgnoredPackage(packageName.trim())
    }

    override fun getSmartNotificationsEnabled(): Flow<Boolean> =
        queries.get("smartNotificationsEnabled").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: true
        }

    override suspend fun setSmartNotificationsEnabled(enabled: Boolean): Unit = withContext(dispatcher) {
        queries.set("smartNotificationsEnabled", enabled.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override suspend fun setCustomAppName(name: String): Unit = withContext(dispatcher) {
        queries.set("custom_app_name", name.take(UserSettings.MAX_APP_NAME_LENGTH), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getCustomAppSubtitle(): Flow<String> =
        queries.get("custom_app_subtitle").asFlow().mapToOneOrNull(dispatcher).map { record ->
            (record?.value_ ?: UserSettings.DEFAULT_APP_SUBTITLE).take(UserSettings.MAX_APP_SUBTITLE_LENGTH)
        }

    override suspend fun setCustomAppSubtitle(subtitle: String): Unit = withContext(dispatcher) {
        queries.set("custom_app_subtitle", subtitle.take(UserSettings.MAX_APP_SUBTITLE_LENGTH), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getShowAppLogo(): Flow<Boolean> =
        queries.get("show_app_logo").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toBooleanStrictOrNull() ?: true
        }

    override suspend fun setShowAppLogo(show: Boolean): Unit = withContext(dispatcher) {
        queries.set("show_app_logo", show.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override suspend fun setNavOrder(order: String): Unit = withContext(dispatcher) {
        queries.set("nav_order", order, com.cashbuddy.platform.currentTimeMillis())
    }

    override suspend fun setShowHealthInTopBar(show: Boolean): Unit = withContext(dispatcher) {
        queries.set("show_health_in_top_bar", show.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getLastSyncTimestamp(): Flow<Long> =
        queries.get("last_sync_timestamp").asFlow().mapToOneOrNull(dispatcher).map { record ->
            record?.value_?.toLongOrNull() ?: 0L
        }

    override suspend fun setLastSyncTimestamp(timestamp: Long): Unit = withContext(dispatcher) {
        queries.set("last_sync_timestamp", timestamp.toString(), com.cashbuddy.platform.currentTimeMillis())
    }

    override fun getDailyWrapUpTime(): Flow<Pair<Int, Int>> =
        queries.getAll().asFlow().mapToList(dispatcher).map { list ->
            val map = list.associate { it.key to it.value_ }
            val hour = map["daily_wrap_up_hour"]?.toIntOrNull() ?: UserSettings.DEFAULT_WRAP_UP_HOUR
            val minute = map["daily_wrap_up_minute"]?.toIntOrNull() ?: UserSettings.DEFAULT_WRAP_UP_MINUTE
            hour to minute
        }

    override suspend fun setDailyWrapUpTime(hour: Int, minute: Int): Unit = withContext(dispatcher) {
        val now = com.cashbuddy.platform.currentTimeMillis()
        db.transaction {
            queries.set("daily_wrap_up_hour", hour.toString(), now)
            queries.set("daily_wrap_up_minute", minute.toString(), now)
        }
    }
}
