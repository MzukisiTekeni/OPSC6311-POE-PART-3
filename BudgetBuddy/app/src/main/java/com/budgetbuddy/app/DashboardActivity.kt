package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.budgetbuddy.app.db.BadgeKeys
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardActivity : AppCompatActivity() {

    private lateinit var repo: BudgetRepository

    // Maps each dashboard badge FrameLayout id → its BadgeKey
    private val dashboardBadgeMap = mapOf(
        R.id.dash_badge_first_budget to BadgeKeys.FIRST_BUDGET,
        R.id.dash_badge_streak_7     to BadgeKeys.STREAK_7,
        R.id.dash_badge_first_saver  to BadgeKeys.FIRST_SAVER,
        R.id.dash_badge_all_goals    to BadgeKeys.ALL_GOALS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        repo = BudgetRepository(this)

        setupBottomNav()
        setupClickListeners()
        observeLiveData()
    }

    // ── Observe Room LiveData ──────────────────────────────────────────────────
    private fun observeLiveData() {
        val userId = SessionManager.getUserId(this)
        val month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

        // User info → avatar + streak
        repo.getLoggedInUser(userId).observe(this) { user ->
            user ?: return@observe
            findViewById<TextView>(R.id.tv_avatar_initials).text = user.avatarInitials
            findViewById<TextView>(R.id.tv_streak_count).text    = user.dayStreak.toString()
            // XP
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

        // Total budget for this month
        repo.getTotalBudget(userId, month).observe(this) { totalBudget ->
            if (totalBudget != null && totalBudget > 0) {
                findViewById<TextView>(R.id.tv_budget_amount).text =
                    "R${"%,.2f".format(totalBudget)}"
            }
        }

        // Total spent this month → updates progress bar + remaining
        repo.getTotalSpentByMonth(userId, month).observe(this) { spent ->
            val s = spent ?: 0.0
            repo.getTotalBudget(userId, month).observe(this) { budget ->
                val b = budget ?: 0.0
                val remaining = (b - s).coerceAtLeast(0.0)
                val pct = if (b > 0) ((s / b) * 100).toInt().coerceIn(0, 100) else 0
                findViewById<TextView>(R.id.tv_remaining).text    = "R${"%,.0f".format(remaining)} remaining"
                findViewById<TextView>(R.id.tv_percent_used).text = "$pct% used"
                findViewById<ProgressBar>(R.id.progress_budget).progress = pct
            }
        }

        // Goals on track count
        repo.getAllGoals(userId).observe(this) { goals ->
            val onTrack = goals.count { !it.isCompleted }
            val total   = goals.size
            findViewById<TextView>(R.id.tv_goals_count).text = "$onTrack/$total"
        }

        // ── Achievements badges — show real earned/locked state ───────────────
        repo.getEarnedBadges(userId).observe(this) { badges ->
            val earnedKeys = badges.map { it.badgeKey }.toSet()
            for ((frameId, badgeKey) in dashboardBadgeMap) {
                val frame = findViewById<FrameLayout?>(frameId) ?: continue
                if (badgeKey in earnedKeys) {
                    frame.alpha = 1f
                } else {
                    frame.alpha = 0.30f
                }
            }
        }
    }

    // ── Bottom Nav ────────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_home

        nav.itemIconTintList = null
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> true
                R.id.nav_expenses -> { start(ViewExpensesActivity::class.java); false }
                R.id.nav_stats    -> { start(StatisticsActivity::class.java); false }
                R.id.nav_savings  -> { start(SavingsGoalsActivity::class.java); false }
                R.id.nav_profile  -> { start(ProfileActivity::class.java); false }
                else -> false
            }
        }
    }

    // ── Click Listeners ───────────────────────────────────────────────────────
    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.iv_notification).setOnClickListener { start(NotificationsActivity::class.java) }
        findViewById<FrameLayout>(R.id.fl_avatar).setOnClickListener      { start(ProfileActivity::class.java) }
        findViewById<Button>(R.id.btn_view_budget).setOnClickListener      { start(MonthlyBudgetActivity::class.java) }
        findViewById<TextView>(R.id.btn_view_profile).setOnClickListener   { start(ProfileActivity::class.java) }
        findViewById<TextView>(R.id.btn_view_profile1).setOnClickListener  { start(ProfileActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_add_categories).setOnClickListener { start(ExpenseCategoriesActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_set_budget).setOnClickListener     { start(MonthlyBudgetActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_add_expense).setOnClickListener    { start(AddExpenseActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_savings_goals).setOnClickListener  { start(SavingsGoalsActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_view_expenses).setOnClickListener  { start(ViewExpensesActivity::class.java) }
        findViewById<LinearLayout>(R.id.action_budget_health).setOnClickListener  { start(BudgetHealthActivity::class.java) }
    }

    private fun <T> start(cls: Class<T>) = startActivity(Intent(this, cls))

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { moveTaskToBack(true) }
}
