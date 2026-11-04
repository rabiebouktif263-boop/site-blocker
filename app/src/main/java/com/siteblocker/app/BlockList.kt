package com.siteblocker.app

import android.content.Context
import java.net.URI

/** Stores the blocked domains and the on/off state in SharedPreferences. */
object BlockList {
    private const val PREFS = "siteblocker"
    private const val KEY_DOMAINS = "domains"
    private const val KEY_ENABLED = "enabled"

    // Only a salted SHA-256 hash of the removal password is stored here, never the password.
    private const val PW_SALT = "123a7a50fc518c77"
    private const val PW_HASH = "6b0c4c4bc8bdb106d6866a191f68443a3fa69611d0742f54295b2b84c0d1ead1"

    fun checkPassword(input: String): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest((PW_SALT + input).toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return java.security.MessageDigest.isEqual(
            hex.toByteArray(Charsets.UTF_8), PW_HASH.toByteArray(Charsets.UTF_8)
        )
    }

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
