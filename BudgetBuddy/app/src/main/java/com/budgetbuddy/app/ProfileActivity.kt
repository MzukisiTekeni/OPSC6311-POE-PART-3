package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BadgeKeys
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var repo: BudgetRepository
    private var userId = -1

    // ── Theme XP thresholds ───────────────────────────────────────────────────
    private val THEME_XP_OCEAN     = 500
    private val THEME_XP_MIDNIGHT  = 2000
    private val THEME_XP_AMBER     = 3500
    private val THEME_XP_VIOLET    = 5000

    // ── Badge view IDs ────────────────────────────────────────────────────────
    // Maps each BadgeKey → Pair(circleFrameLayoutId, labelTextViewId)
    private val badgeViewMap = mapOf(
        BadgeKeys.FIRST_BUDGET     to Pair(R.id.badge_circle_first_budget,     R.id.badge_lbl_first_budget),
        BadgeKeys.STREAK_7         to Pair(R.id.badge_circle_streak_7,         R.id.badge_lbl_streak_7),
        BadgeKeys.FIRST_SAVER      to Pair(R.id.badge_circle_first_saver,      R.id.badge_lbl_first_saver),
        BadgeKeys.STREAK_30        to Pair(R.id.badge_circle_streak_30,        R.id.badge_lbl_streak_30),
        BadgeKeys.ALL_GOALS        to Pair(R.id.badge_circle_all_goals,        R.id.badge_lbl_all_goals),
        BadgeKeys.BUDGET_MASTER    to Pair(R.id.badge_circle_budget_master,    R.id.badge_lbl_budget_master),
        BadgeKeys.NO_SPEND_DAY     to Pair(R.id.badge_circle_no_spend_day,     R.id.badge_lbl_no_spend_day),
        BadgeKeys.GOAL_CRUSHER     to Pair(R.id.badge_circle_goal_crusher,     R.id.badge_lbl_goal_crusher),
        BadgeKeys.CONSISTENT_SAVER to Pair(R.id.badge_circle_consistent_saver, R.id.badge_lbl_consistent_saver)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "Profile"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        // ── Live unread-notification badge ────────────────────────────────────
        repo.countUnread(userId).observe(this) { unread ->
            val badge = findViewById<TextView?>(R.id.tv_notif_badge)
            badge?.let {
                if ((unread ?: 0) > 0) {
                    it.visibility = View.VISIBLE
                    it.text = if (unread!! > 99) "99+" else unread.toString()
                } else {
                    it.visibility = View.GONE
                }
            }
        }

        // ── Observe user data ─────────────────────────────────────────────────
        repo.getLoggedInUser(userId).observe(this) { user ->
            user ?: return@observe

            findViewById<TextView>(R.id.tv_avatar_initials).text = user.avatarInitials
            findViewById<TextView>(R.id.tv_user_name).text       = user.username
            findViewById<TextView>(R.id.tv_level_badge).text     = "★ Lv ${user.level}"

            findViewById<TextView>(R.id.tv_day_streak).text = user.dayStreak.toString()
            findViewById<TextView>(R.id.tv_total_xp).text   = "%,d".format(user.totalXp)
            val savedStr = if (user.totalSaved >= 1000)
                "R${"%.1f".format(user.totalSaved / 1000)}K"
            else
                "R${"%,.0f".format(user.totalSaved)}"
            findViewById<TextView>(R.id.tv_total_saved).text = savedStr

            // XP progress bar
            val maxXp    = user.level * 2000
            val progress = ((user.totalXp.toFloat() / maxXp) * 100).toInt().coerceIn(0, 100)
            findViewById<ProgressBar>(R.id.progress_xp).apply {
                max           = 100
                this.progress = progress
            }

            // XP fraction label  e.g.  "1,340 / 2,000 XP"
            findViewById<TextView>(R.id.tv_xp_fraction).text =
                "%,d / %,d XP".format(user.totalXp, maxXp)

            // XP level title  e.g. "Level 6 · Budget Pro"
            val levelTitle = "Level ${user.level} · ${levelName(user.level)}"
            findViewById<TextView>(R.id.tv_xp_level_title).text = levelTitle

            // XP hint below the bar  e.g.  "660 XP to Level 7 · Midnight theme"
            val xpToNext    = (maxXp - user.totalXp).coerceAtLeast(0)
            val nextUnlock  = nextThemeUnlock(user.totalXp)
            val hintSuffix  = if (nextUnlock != null) " · ${nextUnlock.first}" else ""
            findViewById<TextView>(R.id.tv_xp_hint).text =
                "$xpToNext XP to Level ${user.level + 1}$hintSuffix"

            // Update themes panel whenever XP changes
            updateThemesPanel(user.totalXp)
        }

        // ── Total saved from goals ────────────────────────────────────────────
        repo.getTotalSaved(userId).observe(this) { /* supplements user.totalSaved */ }

        // ── XP earn rows ──────────────────────────────────────────────────────
        setEarnRow(R.id.row_log_expense,   "➕", "Log an expense",         "+10 XP")
        setEarnRow(R.id.row_stay_budget,   "✅", "Stay within budget",      "+50 XP")
        setEarnRow(R.id.row_complete_goal, "🎯", "Complete a savings goal", "+200 XP")

        // ── Tab switching ─────────────────────────────────────────────────────
        selectTab("XP")
        findViewById<TextView>(R.id.tab_xp).setOnClickListener     { selectTab("XP") }
        findViewById<TextView>(R.id.tab_badges).setOnClickListener  { selectTab("Badges") }
        findViewById<TextView>(R.id.tab_themes).setOnClickListener  { selectTab("Themes") }

        // ── Observe earned badges ─────────────────────────────────────────────
        repo.getEarnedBadges(userId).observe(this) { badges ->
            val earned = badges.map { it.badgeKey }.toSet()
            updateBadgesPanel(earned)
        }

        // ── Settings rows ─────────────────────────────────────────────────────
        setSettingsRow(R.id.row_notifications, "Notifications", showToggle = true, toggleOn = true)
        setSettingsRow(R.id.row_currency, "Currency", value = "ZAR • R")
        setSettingsRow(R.id.row_privacy,  "Privacy") {
            Toast.makeText(this, "Privacy settings coming soon", Toast.LENGTH_SHORT).show()
        }

        setSettingsRow(R.id.row_clear_data, "Clear Data") { showClearDataDialog() }

        setSettingsRow(R.id.row_signout, "Sign out") {
            SessionManager.clearSession(this)
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    // ── Tab logic ─────────────────────────────────────────────────────────────

    private fun selectTab(tab: String) {
        // Update chip styles
        listOf(R.id.tab_xp to "XP", R.id.tab_badges to "Badges", R.id.tab_themes to "Themes")
            .forEach { (id, label) ->
                findViewById<TextView>(id).apply {
                    setBackgroundResource(
                        if (label == tab) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
                    )
                    setTextColor(
                        if (label == tab) getColor(R.color.text_on_primary)
                        else              getColor(R.color.text_secondary)
                    )
                }
            }
        // Show/hide panels
        findViewById<View>(R.id.panel_xp).visibility     = if (tab == "XP")     View.VISIBLE else View.GONE
        findViewById<View>(R.id.panel_badges).visibility = if (tab == "Badges") View.VISIBLE else View.GONE
        findViewById<View>(R.id.panel_themes).visibility = if (tab == "Themes") View.VISIBLE else View.GONE
    }

    // ── Badges panel ─────────────────────────────────────────────────────────

    /**
     * For each badge: if earned → show full colour + green label.
     * If not earned → apply greyscale alpha overlay.
     */
    private fun updateBadgesPanel(earned: Set<String>) {
        var earnedCount = 0

        for ((key, ids) in badgeViewMap) {
            val (circleId, labelId) = ids
            val circle = findViewById<FrameLayout>(circleId)
            val label  = findViewById<TextView>(labelId)

            if (key in earned) {
                earnedCount++
                // Full colour — restore alpha and no colour filter
                circle.alpha = 1f
                circle.colorFilter = null
                label.setTextColor(getColor(R.color.primary))
                label.alpha = 1f
            } else {
                // Locked — dim with greyscale effect
                circle.alpha = 0.38f
                label.setTextColor(getColor(R.color.text_hint))
                label.alpha = 0.5f
            }
        }

        // Update count label
        val allBadges = badgeViewMap.size
        findViewById<TextView>(R.id.tv_badge_count).text =
            "$earnedCount of $allBadges badges earned"
    }

    // ── Themes panel ─────────────────────────────────────────────────────────

    private data class ThemeInfo(
        val name: String,
        val xpRequired: Int,
        val containerId: Int,
        val subLabelId: Int
    )

    private val themes = listOf(
        ThemeInfo("Forest",   0,                R.id.theme_forest,   R.id.theme_sub_forest),
        ThemeInfo("Ocean",    THEME_XP_OCEAN,   R.id.theme_ocean,    R.id.theme_sub_ocean),
        ThemeInfo("Midnight", THEME_XP_MIDNIGHT,R.id.theme_midnight, R.id.theme_sub_midnight),
        ThemeInfo("Violet",   THEME_XP_VIOLET,  R.id.theme_violet,   R.id.theme_sub_violet),
        ThemeInfo("Amber",    THEME_XP_AMBER,   R.id.theme_amber,    R.id.theme_sub_amber)
    )

    private fun updateThemesPanel(totalXp: Int) {
        for (theme in themes) {
            val container = findViewById<LinearLayout>(theme.containerId)
            val subLabel  = findViewById<TextView>(theme.subLabelId)

            if (totalXp >= theme.xpRequired) {
                // Unlocked
                container.alpha = 1f
                if (theme.xpRequired == 0) {
                    subLabel.text = "Active"
                    subLabel.setTextColor(getColor(R.color.primary))
                } else {
                    subLabel.text = "Unlocked"
                    subLabel.setTextColor(getColor(R.color.primary))
                }
            } else {
                // Locked
                container.alpha = 0.38f
                // Keep the existing XP label text — it was set in the layout
            }
        }

        // Update the nudge hint in the themes panel
        val next = nextThemeUnlock(totalXp)
        val hint = if (next != null) {
            val xpNeeded = next.second - totalXp
            "⭐ ${"$xpNeeded XP"} away from ${next.first} — keep logging expenses!"
        } else {
            "🎉 All themes unlocked! You're a Budget Legend."
        }
        findViewById<TextView>(R.id.tv_theme_xp_hint).text = hint
    }

    /** Returns the (name, xpRequired) of the next locked theme, or null if all unlocked. */
    private fun nextThemeUnlock(totalXp: Int): Pair<String, Int>? {
        return themes
            .filter { it.xpRequired > 0 && totalXp < it.xpRequired }
            .minByOrNull { it.xpRequired }
            ?.let { Pair(it.name, it.xpRequired) }
    }

    /** Human-readable level name at given level number. */
    private fun levelName(level: Int): String = when {
        level <= 1  -> "Starter"
        level <= 2  -> "Saver"
        level <= 3  -> "Planner"
        level <= 4  -> "Tracker"
        level <= 5  -> "Budget Pro"
        level <= 7  -> "Finance Ace"
        level <= 10 -> "Money Master"
        else        -> "Budget Legend"
    }

    // ── Clear Data dialog ─────────────────────────────────────────────────────

    private fun showClearDataDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_clear_data, null)

        val cbExpenses      = view.findViewById<CheckBox>(R.id.cb_expenses)
        val cbBudgets       = view.findViewById<CheckBox>(R.id.cb_budgets)
        val cbGoals         = view.findViewById<CheckBox>(R.id.cb_goals)
        val cbNotifications = view.findViewById<CheckBox>(R.id.cb_notifications)
        val cbOverall       = view.findViewById<CheckBox>(R.id.cb_overall_budget)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🗑️ Clear Data")
            .setView(view)
            .setPositiveButton("Delete Selected", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val checked = booleanArrayOf(
                    cbExpenses.isChecked,
                    cbBudgets.isChecked,
                    cbGoals.isChecked,
                    cbNotifications.isChecked,
                    cbOverall.isChecked
                )

                if (checked.none { it }) {
                    Toast.makeText(this, "Please select at least one option", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedLabels = listOf(
                    "Expenses & history",
                    "Category budgets",
                    "Savings goals",
                    "Notifications",
                    "Overall budget"
                ).filterIndexed { i, _ -> checked[i] }.joinToString(", ")

                dialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Are you sure?")
                    .setMessage("You are about to permanently delete:\n\n$selectedLabels\n\nThis cannot be undone.")
                    .setPositiveButton("Yes, delete") { _, _ -> performClear(checked) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun performClear(checked: BooleanArray) {
        lifecycleScope.launch {
            if (checked[0]) repo.expenseDao.deleteAllForUser(userId)
            if (checked[1]) {
                repo.budgetDao.deleteAllForUser(userId)
                repo.saveOverallBudget(this@ProfileActivity, userId, 0.0)
            }
            if (checked[2]) repo.savingsGoalDao.deleteAllForUser(userId)
            if (checked[3]) repo.clearAllNotifications(userId)
            if (checked[4]) repo.saveOverallBudget(this@ProfileActivity, userId, 0.0)

            runOnUiThread {
                Toast.makeText(this@ProfileActivity, "Data cleared ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setEarnRow(viewId: Int, icon: String, label: String, xp: String) {
        val row = findViewById<View>(viewId)
        row.findViewById<TextView>(R.id.tv_earn_icon).text  = icon
        row.findViewById<TextView>(R.id.tv_earn_label).text = label
        row.findViewById<TextView>(R.id.tv_earn_xp).text    = xp
    }

    private fun setSettingsRow(
        viewId: Int, label: String,
        value: String = "", showToggle: Boolean = false, toggleOn: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        val row     = findViewById<View>(viewId)
        val tvLabel = row.findViewById<TextView>(R.id.tv_setting_label)
        val tvVal   = row.findViewById<TextView>(R.id.tv_setting_value)
        val ivChev  = row.findViewById<android.widget.ImageView>(R.id.iv_setting_action)
        val toggle  = row.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_toggle)

        tvLabel.text = label
        when {
            showToggle -> {
                toggle.visibility = View.VISIBLE
                ivChev.visibility = View.GONE
                toggle.isChecked  = toggleOn
            }
            value.isNotEmpty() -> {
                tvVal.text       = value
                tvVal.visibility = View.VISIBLE
            }
            else -> ivChev.visibility = View.VISIBLE
        }
        onClick?.let { row.setOnClickListener { it() } }
    }
}
