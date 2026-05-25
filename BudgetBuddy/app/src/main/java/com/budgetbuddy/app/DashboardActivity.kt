package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.budgetbuddy.app.db.BadgeKeys
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardActivity : BaseThemedActivity() {

    private val TAG = "DashboardActivity"

    private lateinit var repo: BudgetRepository

    // Maps each badge FrameLayout on the dashboard to its badge key in the DB
    // This way I can loop over them instead of writing the same logic 4 times
    private val dashboardBadgeMap = mapOf(
        R.id.dash_badge_first_budget to BadgeKeys.FIRST_BUDGET,
        R.id.dash_badge_streak_7     to BadgeKeys.STREAK_7,
        R.id.dash_badge_first_saver  to BadgeKeys.FIRST_SAVER,
        R.id.dash_badge_all_goals    to BadgeKeys.ALL_GOALS
    )

    // ── Themed view IDs ───────────────────────────────────────────────────────
    override fun themedBackgroundViewIds() = listOf(
        R.id.fl_avatar,
        R.id.btn_view_budget
    )
    override fun themedProgressBarIds() = listOf(
        R.id.progress_xp,
        R.id.progress_budget
    )
    override fun themedTextViewIds() = listOf(
        R.id.btn_view_profile,
        R.id.btn_view_profile1
    )
    override fun bottomNavId() = R.id.bottom_nav

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        repo = BudgetRepository(this)

        Log.d(TAG, "onCreate: Dashboard loaded for userId=${SessionManager.getUserId(this)}")

        setupBottomNav()
        setupClickListeners()
        observeLiveData()
        // applyCurrentTheme() gets called automatically in onResume() from BaseThemedActivity
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Dashboard came back into focus - theme and data will refresh")
    }

    private fun observeLiveData() {
        val userId = SessionManager.getUserId(this)
        // Format the current month as "yyyy-MM" since that's how it's stored in the DB
        val month  = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

        Log.d(TAG, "observeLiveData: Setting up observers for userId=$userId, month=$month")

        // Observe the logged-in user to display their name, XP, streak, etc.
        repo.getLoggedInUser(userId).observe(this) { user ->
            user ?: run {
                Log.w(TAG, "observeLiveData: User data came back null for userId=$userId")
                return@observe
            }

            Log.d(TAG, "User data updated - level=${user.level}, totalXp=${user.totalXp}, streak=${user.dayStreak}")

            findViewById<TextView>(R.id.tv_avatar_initials).text = user.avatarInitials
            findViewById<TextView>(R.id.tv_streak_count).text    = user.dayStreak.toString()

            // Calculate XP needed to reach the next level
            val xpToNext = ((user.level * 2000) - user.totalXp).coerceAtLeast(0)
            findViewById<TextView>(R.id.tv_xp_count).text =
                "${ "%,d".format(user.totalXp)} of ${user.level * 2000} XP"
            findViewById<TextView>(R.id.tv_xp_hint).text =
                "$xpToNext XP to unlock next theme"
            findViewById<ProgressBar>(R.id.progress_xp).apply {
                max      = user.level * 2000
                progress = user.totalXp
            }
        }

        // Observe total budget for the current month
        repo.getTotalBudget(userId, month).observe(this) { totalBudget ->
            if (totalBudget != null && totalBudget > 0) {
                Log.d(TAG, "Total budget for $month: R$totalBudget")
                findViewById<TextView>(R.id.tv_budget_amount).text =
                    "R${"%,.2f".format(totalBudget)}"
            } else {
                Log.d(TAG, "No budget set for $month yet")
            }
        }

        // Observe how much has been spent so we can calculate what's left
        repo.getTotalSpentByMonth(userId, month).observe(this) { spent ->
            val s = spent ?: 0.0
            repo.getTotalBudget(userId, month).observe(this) { budget ->
                val b         = budget ?: 0.0
                val remaining = (b - s).coerceAtLeast(0.0)
                val pct       = if (b > 0) ((s / b) * 100).toInt().coerceIn(0, 100) else 0

                Log.d(TAG, "Budget progress: spent=R$s / budget=R$b ($pct% used, R$remaining remaining)")

                findViewById<TextView>(R.id.tv_remaining).text    = "R${"%,.0f".format(remaining)} remaining"
                findViewById<TextView>(R.id.tv_percent_used).text = "$pct% used"
                findViewById<ProgressBar>(R.id.progress_budget).progress = pct
            }
        }

        // Show how many savings goals are still in progress vs completed
        repo.getAllGoals(userId).observe(this) { goals ->
            val onTrack = goals.count { !it.isCompleted }
            val total   = goals.size
            Log.d(TAG, "Savings goals: $onTrack active out of $total total")
            findViewById<TextView>(R.id.tv_goals_count).text = "$onTrack/$total"
        }

        // Update badge opacity on the dashboard - earned = full, unearned = faded out
        repo.getEarnedBadges(userId).observe(this) { badges ->
            val earnedKeys = badges.map { it.badgeKey }.toSet()
            Log.d(TAG, "Badges earned: ${earnedKeys.size} - keys: $earnedKeys")

            for ((frameId, badgeKey) in dashboardBadgeMap) {
                val frame = findViewById<FrameLayout?>(frameId) ?: continue
                val isEarned = badgeKey in earnedKeys
                frame.alpha = if (isEarned) 1f else 0.30f

                if (isEarned) Log.d(TAG, "Badge '$badgeKey' is earned - showing at full opacity")
            }
        }
    }

    private fun setupBottomNav() {
        Log.d(TAG, "setupBottomNav: Initialising bottom navigation bar")
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_home

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> {
                    Log.d(TAG, "Bottom nav: Home tab selected (already here)")
                    true
                }
                R.id.nav_expenses -> {
                    Log.d(TAG, "Bottom nav: Expenses tab selected")
                    start(ViewExpensesActivity::class.java); false
                }
                R.id.nav_stats    -> {
                    Log.d(TAG, "Bottom nav: Statistics tab selected")
                    start(StatisticsActivity::class.java); false
                }
                R.id.nav_savings  -> {
                    Log.d(TAG, "Bottom nav: Savings Goals tab selected")
                    start(SavingsGoalsActivity::class.java); false
                }
                R.id.nav_profile  -> {
                    Log.d(TAG, "Bottom nav: Profile tab selected")
                    start(ProfileActivity::class.java); false
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Attaching click listeners to dashboard action buttons")

        findViewById<android.widget.ImageView>(R.id.iv_notification).setOnClickListener {
            Log.d(TAG, "Notification bell tapped")
            start(NotificationsActivity::class.java)
        }
        findViewById<FrameLayout>(R.id.fl_avatar).setOnClickListener {
            Log.d(TAG, "Avatar tapped - going to profile")
            start(ProfileActivity::class.java)
        }
        findViewById<Button>(R.id.btn_view_budget).setOnClickListener {
            Log.d(TAG, "View Budget button tapped")
            start(MonthlyBudgetActivity::class.java)
        }
        findViewById<TextView>(R.id.btn_view_profile).setOnClickListener {
            Log.d(TAG, "View Profile link tapped")
            start(ProfileActivity::class.java)
        }
        findViewById<TextView>(R.id.btn_view_profile1).setOnClickListener {
            Log.d(TAG, "View Profile link (second) tapped")
            start(ProfileActivity::class.java)
        }

        // Quick-action grid at the bottom of the dashboard
        findViewById<LinearLayout>(R.id.action_add_categories).setOnClickListener {
            Log.d(TAG, "Quick action: Add Categories")
            start(ExpenseCategoriesActivity::class.java)
        }
        findViewById<LinearLayout>(R.id.action_set_budget).setOnClickListener {
            Log.d(TAG, "Quick action: Set Budget")
            start(MonthlyBudgetActivity::class.java)
        }
        findViewById<LinearLayout>(R.id.action_add_expense).setOnClickListener {
            Log.d(TAG, "Quick action: Add Expense")
            start(AddExpenseActivity::class.java)
        }
        findViewById<LinearLayout>(R.id.action_savings_goals).setOnClickListener {
            Log.d(TAG, "Quick action: Savings Goals")
            start(SavingsGoalsActivity::class.java)
        }
        findViewById<LinearLayout>(R.id.action_view_expenses).setOnClickListener {
            Log.d(TAG, "Quick action: View Expenses")
            start(ViewExpensesActivity::class.java)
        }
        findViewById<LinearLayout>(R.id.action_budget_health).setOnClickListener {
            Log.d(TAG, "Quick action: Budget Health")
            start(BudgetHealthActivity::class.java)
        }
    }

    // Convenience method so I don't have to write Intent(this, X::class.java) every time
    private fun <T> start(cls: Class<T>) = startActivity(Intent(this, cls))

    // Prevent the user from going back to the login screen from the dashboard
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed: Moving task to background instead of finishing")
        moveTaskToBack(true)
    }
}
