package com.syncro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.syncro.data.local.toLocalTimeOrMidnight
import com.syncro.data.local.toStoredTime
import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.AssistantSettings
import com.syncro.domain.model.DigestSettings
import com.syncro.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Ajustes en DataStore; lo que no esté guardado toma el valor de fábrica de [AppSettings]. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val MORNING_ENABLED = booleanPreferencesKey("digest_morning_enabled")
    private val MORNING_TIME = stringPreferencesKey("digest_morning_time")
    private val EVENING_ENABLED = booleanPreferencesKey("digest_evening_enabled")
    private val EVENING_TIME = stringPreferencesKey("digest_evening_time")
    private val FOCUS_ENABLED = booleanPreferencesKey("assistant_focus_enabled")
    private val LEFTOVERS_ENABLED = booleanPreferencesKey("assistant_leftovers_enabled")
    private val PAYDAY_DAY = intPreferencesKey("assistant_payday_day")
    private val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("assistant_budget_alerts_enabled")
    private val DAILY_QUOTE_ENABLED = booleanPreferencesKey("assistant_daily_quote_enabled")
    private val HOME_SAVINGS_ENABLED = booleanPreferencesKey("home_savings_enabled")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            digest = DigestSettings(
                morningEnabled = prefs[MORNING_ENABLED] ?: defaults.digest.morningEnabled,
                morningTime = prefs[MORNING_TIME]?.toLocalTimeOrMidnight() ?: defaults.digest.morningTime,
                eveningEnabled = prefs[EVENING_ENABLED] ?: defaults.digest.eveningEnabled,
                eveningTime = prefs[EVENING_TIME]?.toLocalTimeOrMidnight() ?: defaults.digest.eveningTime
            ),
            assistant = AssistantSettings(
                focusEnabled = prefs[FOCUS_ENABLED] ?: defaults.assistant.focusEnabled,
                leftoversEnabled = prefs[LEFTOVERS_ENABLED] ?: defaults.assistant.leftoversEnabled,
                paydayDay = prefs[PAYDAY_DAY]?.takeIf { it in 1..31 },
                budgetAlertsEnabled = prefs[BUDGET_ALERTS_ENABLED] ?: defaults.assistant.budgetAlertsEnabled,
                dailyQuoteEnabled = prefs[DAILY_QUOTE_ENABLED] ?: defaults.assistant.dailyQuoteEnabled,
                homeSavingsEnabled = prefs[HOME_SAVINGS_ENABLED] ?: defaults.assistant.homeSavingsEnabled
            ),
            onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted
        )
    }

    override suspend fun save(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[MORNING_ENABLED] = settings.digest.morningEnabled
            prefs[MORNING_TIME] = settings.digest.morningTime.toStoredTime()
            prefs[EVENING_ENABLED] = settings.digest.eveningEnabled
            prefs[EVENING_TIME] = settings.digest.eveningTime.toStoredTime()
            prefs[FOCUS_ENABLED] = settings.assistant.focusEnabled
            prefs[LEFTOVERS_ENABLED] = settings.assistant.leftoversEnabled
            prefs[BUDGET_ALERTS_ENABLED] = settings.assistant.budgetAlertsEnabled
            prefs[DAILY_QUOTE_ENABLED] = settings.assistant.dailyQuoteEnabled
            prefs[HOME_SAVINGS_ENABLED] = settings.assistant.homeSavingsEnabled
            prefs[ONBOARDING_COMPLETED] = settings.onboardingCompleted
            // Sin nómina no se guarda nada: así lo de fábrica (null) vuelve tal cual
            val payday = settings.assistant.paydayDay
            if (payday != null) prefs[PAYDAY_DAY] = payday else prefs.remove(PAYDAY_DAY)
        }
    }
}
