package com.budgetbuddy.app

import android.app.Application

/**
 * BudgetBuddyApp
 * Restores the user's dark-mode preference before any Activity starts,
 * so the correct theme is applied from the very first frame.
 */
class BudgetBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DarkModeManager.applyOnStartup(this)
    }
}
