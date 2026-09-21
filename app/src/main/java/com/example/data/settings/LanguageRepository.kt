package com.example.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Supported UI languages. English intentionally remains the default. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    PERSIAN("fa")
}

class LanguageRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val initialLanguage = preferences.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.tag)
        ?.let { tag -> AppLanguage.entries.firstOrNull { it.tag == tag } }
        ?: AppLanguage.ENGLISH

    private val _language = MutableStateFlow(initialLanguage)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        if (_language.value == language) return
        preferences.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    companion object {
        private const val PREFERENCES = "salvia_preferences"
        private const val KEY_LANGUAGE = "ui_language"
    }
}
