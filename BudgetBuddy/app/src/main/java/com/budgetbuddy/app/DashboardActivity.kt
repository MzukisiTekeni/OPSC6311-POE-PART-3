package com.budgetbuddy.app

import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import com.budgetbuddy.app.db.BadgeKeys
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DashboardActivity : BaseThemedActivity() {

    /* ═══════════════════════════════════════════════════════════════
       SECTION 1 — DATA BINDING & CONFIG
       ═══════════════════════════════════════════════════════════════ */
    // Initialises the repository and mappings for badges/themed views.
    private lateinit var repo: BudgetRepository
    private var prevUnreadCount = -1   // -1 = not yet initialised

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

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        repo = BudgetRepository(this)

        setupBottomNav()
        setupClickListeners()
        observeLiveData()

        // Handle back button to prevent accidental exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        // applyCurrentTheme() is called automatically by onResume()
    }

    private fun observeLiveData() {
        /* ═══════════════════════════════════════════════════════════════
           SECTION 2 — DASHBOARD METRICS
           ═══════════════════════════════════════════════════════════════ */
        // Observes XP progress, budget status, and goals for real-time updates.
        val userId = SessionManager.getUserId(this)
        val month  = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

        repo.getLoggedInUser(userId).observe(this) { user ->
            user ?: return@observe
            findViewById<TextView>(R.id.tv_avatar_initials).text = user.avatarInitials
            findViewById<TextView>(R.id.tv_streak_count).text    = user.dayStreak.toString()
            val xpToNext = ((user.level * 2000) - user.totalXp).coerceAtLeast(0)
            
            // Format XP count using resource string for better maintainability and translation
            val xpMax = user.level * 2000
            val formattedXp = "%,d".format(user.totalXp)
            findViewById<TextView>(R.id.tv_xp_count).text = "$formattedXp of $xpMax XP"
            
            findViewById<TextView>(R.id.tv_xp_hint).text =
                "$xpToNext XP to unlock next theme"
            findViewById<ProgressBar>(R.id.progress_xp).apply {
                max      = user.level * 2000
                progress = user.totalXp
            }
        }

        repo.getTotalBudget(userId, month).observe(this) { totalBudget ->
            val budget = totalBudget ?: 0.0
            findViewById<TextView>(R.id.tv_budget_amount).text =
                "R${"%,.2f".format(budget)}"
        }

        // Use distinct observers and combine state locally to avoid nested observer leaks
        var currentSpent = 0.0
        var currentBudget = 0.0

        fun updateBudgetProgress() {
            val remaining = (currentBudget - currentSpent).coerceAtLeast(0.0)
            val pct       = if (currentBudget > 0) ((currentSpent / currentBudget) * 100).toInt().coerceIn(0, 100) else 0
            findViewById<TextView>(R.id.tv_remaining).text    = "R${"%,.0f".format(remaining)} remaining"
            findViewById<TextView>(R.id.tv_percent_used).text = "$pct% used"
            findViewById<ProgressBar>(R.id.progress_budget).progress = pct
        }

        repo.getTotalSpentByMonth(userId, month).observe(this) { spent ->
            currentSpent = spent ?: 0.0
            updateBudgetProgress()
        }

        repo.getTotalBudget(userId, month).observe(this) { budget ->
            currentBudget = budget ?: 0.0
            updateBudgetProgress()
        }

        repo.getAllGoals(userId).observe(this) { goals ->
            val onTrack = goals.count { !it.isCompleted }
            val total   = goals.size
            findViewById<TextView>(R.id.tv_goals_count).text = "$onTrack/$total"
        }

        // ── Notification badge count ─────────────────────────────────────────
        /* ═══════════════════════════════════════════════════════════════
           SECTION 3 — NOTIFICATIONS & FEEDBACK
           ═══════════════════════════════════════════════════════════════ */
        // Updates the unread count badge and triggers notification sound if needed.
        repo.countUnread(userId).observe(this) { count ->
            val badge = findViewById<TextView>(R.id.tv_notif_count)
            if (count > 0) {
                badge.visibility = View.VISIBLE
                badge.text = if (count > 99) "99+" else count.toString()
                // Play notification sound only when count increases
                if (prevUnreadCount >= 0 && count > prevUnreadCount) {
                    try {
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        MediaPlayer().apply {
                            setAudioStreamType(AudioManager.STREAM_NOTIFICATION)
                            setDataSource(this@DashboardActivity, uri)
                            setOnCompletionListener { release() }
                            prepare()
                            start()
                        }
                    } catch (_: Exception) {}
                }
            } else {
                badge.visibility = View.GONE
            }
            prevUnreadCount = count
        }

        repo.getEarnedBadges(userId).observe(this) { badges ->
            val earnedKeys = badges.map { it.badgeKey }.toSet()
            for ((frameId, badgeKey) in dashboardBadgeMap) {
                val frame = findViewById<FrameLayout?>(frameId) ?: continue
                frame.alpha = if (badgeKey in earnedKeys) 1f else 0.30f
            }
        }
    }

    private fun setupBottomNav() {
        /* ═══════════════════════════════════════════════════════════════
           SECTION 4 — NAVIGATION & INTERACTION
           ═══════════════════════════════════════════════════════════════ */
        // Configures the bottom navigation bar and activity shortcuts.
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.selectedItemId = R.id.nav_home
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

    private fun setupClickListeners() {
        findViewById<FrameLayout>(R.id.iv_notification).setOnClickListener { start(NotificationsActivity::class.java) }
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

}
