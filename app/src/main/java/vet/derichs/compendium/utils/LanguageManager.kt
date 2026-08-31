package vet.derichs.compendium.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import java.util.Locale

class LanguageManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "LanguageManager"
        private const val PREFS_NAME = "language_prefs"
        private const val KEY_LANGUAGE = "selected_language"
        private const val DEFAULT_LANGUAGE = "fr" // French as default

        val SUPPORTED_LANGUAGES = listOf("fr", "nl")
    }

    fun getCurrentLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun setLanguage(languageCode: String) {
        if (languageCode in SUPPORTED_LANGUAGES) {
            prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
            Log.d(TAG, "Language set to: $languageCode")
        } else {
            Log.w(TAG, "Unsupported language code: $languageCode")
        }
    }

    fun detectAndSetDefaultLanguage() {
        val savedLanguage = getCurrentLanguage()
        if (savedLanguage.isNotEmpty()) {
            Log.d(TAG, "Using saved language: $savedLanguage")
            return
        }

        // Detect system language
        val systemLanguage = context.resources.configuration.locales[0].language

        val defaultLang = when (systemLanguage) {
            "nl" -> "nl"
            "fr" -> "fr"
            else -> DEFAULT_LANGUAGE // Default to French for any other language
        }

        setLanguage(defaultLang)
        Log.d(TAG, "Auto-detected language: $systemLanguage, set to: $defaultLang")
    }

    fun getOtherLanguage(): String {
        val current = getCurrentLanguage()
        return when (current) {
            "fr" -> "nl"
            "nl" -> "fr"
            else -> "nl" // Default to Dutch if current language is unexpected
        }
    }

    fun getLanguageDisplayName(languageCode: String): String {
        return when (languageCode) {
            "fr" -> "Français"
            "nl" -> "Nederlands"
            else -> languageCode
        }
    }

    fun getOtherLanguageShortName(): String {
        val otherLang = getOtherLanguage()
        return when (otherLang) {
            "fr" -> "FR"
            "nl" -> "NL"
            else -> "EN"
        }
    }

    fun applyLanguageToContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))

        return context.createConfigurationContext(configuration)
    }
}
