package com.bigdictaphone.app.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bigdictaphone.app.data.Stakeholder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Service for managing app preferences using DataStore
 */
class PreferencesService(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    
    // MARK: - API Key
    
    val geminiApiKey: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }
    
    suspend fun saveGeminiApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }
    
    suspend fun clearGeminiApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(GEMINI_API_KEY)
        }
    }
    
    val hasApiKey: Flow<Boolean> = geminiApiKey.map { it.isNotBlank() }
    
    // MARK: - User Email
    
    val userEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL] ?: ""
    }
    
    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_EMAIL] = email
        }
    }
    
    // MARK: - Auto-Send
    
    val autoSendEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_SEND_ENABLED] ?: false
    }
    
    suspend fun setAutoSendEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SEND_ENABLED] = enabled
        }
    }
    
    // MARK: - Default Language (AUTO, DUTCH, ENGLISH)
    
    val defaultLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_LANGUAGE] ?: "AUTO"
    }
    
    suspend fun saveDefaultLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_LANGUAGE] = language
        }
    }
    
    // MARK: - Stakeholders
    
    val stakeholders: Flow<List<Stakeholder>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[STAKEHOLDERS] ?: "[]"
        try {
            json.decodeFromString<List<Stakeholder>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun saveStakeholders(stakeholders: List<Stakeholder>) {
        context.dataStore.edit { preferences ->
            preferences[STAKEHOLDERS] = json.encodeToString(stakeholders)
        }
    }
    
    suspend fun addStakeholder(stakeholder: Stakeholder) {
        val current = stakeholders.first()
        saveStakeholders(current + stakeholder)
    }
    
    suspend fun updateStakeholder(stakeholder: Stakeholder) {
        val current = stakeholders.first()
        saveStakeholders(current.map { if (it.id == stakeholder.id) stakeholder else it })
    }
    
    suspend fun removeStakeholder(stakeholder: Stakeholder) {
        val current = stakeholders.first()
        saveStakeholders(current.filter { it.id != stakeholder.id })
    }

    // MARK: - SMTP Settings
    
    val smtpHost: Flow<String> = context.dataStore.data.map { it[SMTP_HOST] ?: "" }
    val smtpPort: Flow<String> = context.dataStore.data.map { it[SMTP_PORT] ?: "" }
    val smtpUser: Flow<String> = context.dataStore.data.map { it[SMTP_USER] ?: "" }
    val smtpPass: Flow<String> = context.dataStore.data.map { it[SMTP_PASS] ?: "" }

    suspend fun saveSmtpSettings(host: String, port: String, user: String, pass: String) {
        context.dataStore.edit { preferences ->
            preferences[SMTP_HOST] = host
            preferences[SMTP_PORT] = port
            preferences[SMTP_USER] = user
            preferences[SMTP_PASS] = pass
        }
    }

    companion object {
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        private val AUTO_SEND_ENABLED = booleanPreferencesKey("auto_send_enabled")
        private val STAKEHOLDERS = stringPreferencesKey("stakeholders")
        private val SMTP_HOST = stringPreferencesKey("smtp_host")
        private val SMTP_PORT = stringPreferencesKey("smtp_port")
        private val SMTP_USER = stringPreferencesKey("smtp_user")
        private val SMTP_PASS = stringPreferencesKey("smtp_pass")
    }
}
