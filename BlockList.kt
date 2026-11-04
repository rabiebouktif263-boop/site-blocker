package com.siteblocker.app

import android.content.Context
import java.net.URI

/** Stores the blocked domains and the on/off state in SharedPreferences. */
object BlockList {
    private const val PREFS = "siteblocker"
    private const val KEY_DOMAINS = "domains"
    private const val KEY_ENABLED = "enabled"

    /** Turns "https://www.example.com/page?x=1" into "example.com". Returns null if invalid. */
    fun extractDomain(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "http://$s"
        val host = try {
            URI(s).host
        } catch (e: Exception) {
            null
        } ?: return null
        val d = host.removePrefix("www.").trim('.')
        val valid = d.contains('.') && d.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return if (valid) d else null
    }

    fun getAll(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_DOMAINS, emptySet())?.toSet() ?: emptySet()

    private fun save(ctx: Context, set: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_DOMAINS, set).apply()
    }

    fun add(ctx: Context, domain: String) = save(ctx, getAll(ctx) + domain)

    fun remove(ctx: Context, domain: String) = save(ctx, getAll(ctx) - domain)

    /** True if host is a blocked domain or any subdomain of one. */
    fun isBlocked(ctx: Context, host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return getAll(ctx).any { h == it || h.endsWith(".$it") }
    }

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
