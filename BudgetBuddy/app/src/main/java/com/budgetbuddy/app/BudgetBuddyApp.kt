package com.budgetbuddy.app

import android.app.Application
import android.util.Log

/**
 * BudgetBuddyApp - the Application class that runs before anything else.
 *
 * I'm applying dark mode here in onCreate() so the correct theme is set
 * before any Activity inflates its layout. If I did it in an Activity instead,
 * you'd see a flash of the wrong theme on startup.
 */
class BudgetBuddyApp : Application() {

    private val TAG = "BudgetBuddyApp"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Application starting up - applying dark mode preference")
        DarkModeManager.applyOnStartup(this)
        Log.d(TAG, "onCreate: BudgetBuddy is ready")
    }
}
