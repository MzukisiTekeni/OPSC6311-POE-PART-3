package com.budgetbuddy.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * DarkModeManager
 * ──────────────────────────────────────────────────────────────────
 * Manages the Dark Mode toggle as an *independent* layer that sits
 * on top of whichever XP theme the user has selected.
 *
 * Dark mode changes backgrounds/surfaces/text to dark values via
 * the values-night/colors.xml resource qualifier.
 * Theme accent colours (primary, primaryLight, etc.) are applied
 * programmatically by ThemeManager and are NOT affected.
 *
 * This means every combination is valid:
 *   ✔  Light mode  +  Forest theme
 *   ✔  Light mode  +  Ocean theme
 *   ✔  Dark mode   +  Forest theme   ← dark surfaces, green accents
 *   ✔  Dark mode   +  Violet theme   ← dark surfaces, violet accents
 *   …etc.
 *
 * Usage
 * ─────
 * • In Application.onCreate():
 *     DarkModeManager.applyOnStartup(this)
 *
 * • To toggle (e.g. from ProfileActivity switch):
 *     DarkModeManager.setDarkMode(this, enabled)
 *
 * • To read current state (e.g. to initialise a Switch):
 *     DarkModeManager.isDarkMode(context)
 */
object DarkModeManager {

    private const val PREFS        = "budgetbuddy_dark_mode"
    private const val KEY_DARK     = "dark_mode_enabled"

    // ── Persistence ───────────────────────────────────────────────────────────

    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()
        applyDelegate(enabled)
    }

    /** Call once in Application.onCreate() to restore the user's preference. */
    fun applyOnStartup(context: Context) {
        applyDelegate(isDarkMode(context))
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun applyDelegate(dark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else      AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
