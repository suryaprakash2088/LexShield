package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lexShieldDataStore by preferencesDataStore(name = "lexshield_preferences")

enum class AppThemeMode(val label: String, val description: String) {
    LIGHT("Light", "Ivory & judicial navy (Default)"),
    DARK("Dark", "Low-glare dark mode"),
    SYSTEM("System", "Match device display setting");

    companion object {
        fun fromRaw(raw: String?): AppThemeMode =
            entries.firstOrNull { it.name == raw } ?: LIGHT
    }
}

class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val themeKey = stringPreferencesKey("app_theme_mode")

    val themeMode: Flow<AppThemeMode> = appContext.lexShieldDataStore.data.map { prefs ->
        AppThemeMode.fromRaw(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.lexShieldDataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
}
