package com.example.ecosort.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.model.UserPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesManager(private val context: Context) {

    private val gson = Gson()

    private object PreferencesKeys {
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val USER_TYPE = stringPreferencesKey("user_type")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val USER_PREFERENCES = stringPreferencesKey("user_preferences")
    }

    // ==================== USER SESSION ====================

    val userSession: Flow<UserSession?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
            if (isLoggedIn) {
                UserSession(
                    userId = preferences[PreferencesKeys.USER_ID] ?: 0L,
                    username = preferences[PreferencesKeys.USERNAME] ?: "",
                    userType = UserType.valueOf(
                        preferences[PreferencesKeys.USER_TYPE] ?: UserType.USER.name
                    ),
                    token = preferences[PreferencesKeys.SESSION_TOKEN],
                    isLoggedIn = true
                )
            } else {
                null
            }
        }

    suspend fun saveUserSession(session: UserSession) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_ID] = session.userId
            preferences[PreferencesKeys.USERNAME] = session.username
            preferences[PreferencesKeys.USER_TYPE] = session.userType.name
            preferences[PreferencesKeys.IS_LOGGED_IN] = session.isLoggedIn
            session.token?.let { preferences[PreferencesKeys.SESSION_TOKEN] = it }
        }
        android.util.Log.d("UserPreferencesManager", "User session saved: ${session.username} (ID: ${session.userId})")
    }

    suspend fun clearUserSession() {
        try {
            context.dataStore.edit { preferences ->
                preferences.remove(PreferencesKeys.USER_ID)
                preferences.remove(PreferencesKeys.USERNAME)
                preferences.remove(PreferencesKeys.USER_TYPE)
                preferences.remove(PreferencesKeys.SESSION_TOKEN)
                preferences[PreferencesKeys.IS_LOGGED_IN] = false
            }
            android.util.Log.d("UserPreferencesManager", "User session cleared successfully")
        } catch (e: Exception) {
            android.util.Log.e("UserPreferencesManager", "Error clearing user session", e)
            throw e
        }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
        }

    // ==================== APP PREFERENCES ====================

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME_MODE] ?: "system"
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    val language: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE] ?: "en"
        }

    // ==================== HELPER METHODS ====================

    suspend fun getCurrentUser(): UserSession? {
        return try {
            val preferences = context.dataStore.data.first()
            val isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
            if (isLoggedIn) {
                val userSession = UserSession(
                    userId = preferences[PreferencesKeys.USER_ID] ?: 0L,
                    username = preferences[PreferencesKeys.USERNAME] ?: "",
                    userType = UserType.valueOf(
                        preferences[PreferencesKeys.USER_TYPE] ?: UserType.USER.name
                    ),
                    token = preferences[PreferencesKeys.SESSION_TOKEN],
                    isLoggedIn = true
                )
                android.util.Log.d("UserPreferencesManager", "Current user loaded: ${userSession.username} (ID: ${userSession.userId})")
                userSession
            } else {
                android.util.Log.d("UserPreferencesManager", "No user logged in")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UserPreferencesManager", "Error getting current user", e)
            null
        }
    }
    
    // ==================== USER PREFERENCES MANAGEMENT ====================
    
    suspend fun getUserPreferences(): UserPreferences {
        return try {
            val preferences = context.dataStore.data.first()
            val preferencesJson = preferences[PreferencesKeys.USER_PREFERENCES]
            if (preferencesJson != null) {
                gson.fromJson(preferencesJson, UserPreferences::class.java)
            } else {
                UserPreferences() // Return default preferences
            }
        } catch (e: Exception) {
            android.util.Log.e("UserPreferencesManager", "Error getting user preferences", e)
            UserPreferences() // Return default preferences on error
        }
    }
    
    suspend fun saveUserPreferences(preferences: UserPreferences) {
        try {
            context.dataStore.edit { dataStore ->
                dataStore[PreferencesKeys.USER_PREFERENCES] = gson.toJson(preferences)
            }
            android.util.Log.d("UserPreferencesManager", "User preferences saved successfully")
        } catch (e: Exception) {
            android.util.Log.e("UserPreferencesManager", "Error saving user preferences", e)
            throw e
        }
    }
}