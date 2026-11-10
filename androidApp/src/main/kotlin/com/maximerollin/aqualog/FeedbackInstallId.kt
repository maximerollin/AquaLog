package com.maximerollin.aqualog

import android.content.Context
import java.util.UUID

/** A random id that lets the server rate-limit anonymous feedback; it is dropped with the account. */
internal interface FeedbackInstallIdStore {
    fun getOrCreate(): String
    fun clear()
}

internal class AndroidFeedbackInstallIdStore(context: Context) : FeedbackInstallIdStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    override fun getOrCreate(): String {
        preferences.getString(KEY, null)?.let { return it }
        val created = UUID.randomUUID().toString()
        preferences.edit().putString(KEY, created).apply()
        return created
    }

    @Synchronized
    override fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREFERENCES = "aqualog-feedback"
        const val KEY = "install-id"
    }
}
