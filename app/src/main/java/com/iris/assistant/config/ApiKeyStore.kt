package com.iris.assistant.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the OpenAI API key (and which model to use) encrypted on-device,
 * via Android's own Keystore-backed EncryptedSharedPreferences.
 *
 * IMPORTANT: the key is only ever entered by the user from the Settings
 * screen and lives only in this encrypted file — it is never written into
 * source code, never committed to git, and never sent anywhere except
 * directly to https://api.openai.com over HTTPS.
 */
object ApiKeyStore {
    private const val PREFS_NAME = "iris_secure_prefs"
    private const val KEY_API_KEY = "openai_api_key"
    private const val KEY_MODEL = "openai_model"
    private const val KEY_BASE_URL = "openai_base_url"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    // Base URL WITHOUT the trailing "/chat/completions" — that's appended in
    // code, matching both OpenAI's SDK convention and Cloudflare Workers AI's
    // documented "baseURL" style (https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/).
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cachedPrefs?.let { return it }
        synchronized(this) {
            cachedPrefs?.let { return it }
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cachedPrefs = created
            return created
        }
    }

    fun getApiKey(context: Context): String? =
        prefs(context).getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API_KEY).apply()
    }

    fun getModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    /** Base URL only, e.g. "https://api.openai.com/v1" — no trailing slash, no "/chat/completions". */
    fun getBaseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL)?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, baseUrl: String) {
        val cleaned = baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        prefs(context).edit().putString(KEY_BASE_URL, cleaned.ifBlank { DEFAULT_BASE_URL }).apply()
    }
}
