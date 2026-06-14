package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SavingsGoalEntity
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class GoalAdapter(
    private val goals: MutableList<SavingsGoalEntity>,
    private val onLongPress: (SavingsGoalEntity) -> Unit,
    private val onAddContribution: (SavingsGoalEntity) -> Unit
) : RecyclerView.Adapter<GoalAdapter.VH>() {

    /* ═══════════════════════════════════════════════════════════════
       SECTION 1 — RECYCLERVIEW ADAPTER
       ═══════════════════════════════════════════════════════════════ */
    // Manages the display of individual savings goal cards and their progress.
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView        = v.findViewById(R.id.tv_goal_name)
        val due: TextView         = v.findViewById(R.id.tv_goal_due)
        val saved: TextView       = v.findViewById(R.id.tv_goal_saved)
        val target: TextView      = v.findViewById(R.id.tv_goal_target)
        val remaining: TextView   = v.findViewById(R.id.tv_goal_remaining)
        val percent: TextView     = v.findViewById(R.id.tv_goal_percent)
        val progress: ProgressBar = v.findViewById(R.id.progress_goal)
        val btnAdd: TextView      = v.findViewById(R.id.btn_add_contribution)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_savings_goal_card, p, false))

    override fun getItemCount() = goals.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val g   = goals[pos]
        val pct = if (g.targetAmount > 0)
            ((g.savedAmount / g.targetAmount) * 100).toInt().coerceIn(0, 100) else 0
        h.name.text      = g.name
        h.due.text       = if (g.isCompleted) "Completed ${g.completedDate}"
                           else "Due ${g.targetDate} • ${g.frequency}"
        h.saved.text     = "R${"%,.0f".format(g.savedAmount)}"
        h.target.text    = "of R${"%,.0f".format(g.targetAmount)}"
        h.remaining.text = if (g.isCompleted) "✓ Completed!"
                           else "R${"%,.0f".format((g.targetAmount - g.savedAmount).coerceAtLeast(0.0))} to go"
        h.percent.text   = "$pct% complete"
        h.progress.progress = pct
        h.btnAdd.visibility = if (g.isCompleted) View.GONE else View.VISIBLE
        h.btnAdd.setOnClickListener { onAddContribution(g) }
        h.itemView.setOnLongClickListener { if (!g.isCompleted) onLongPress(g); true }

        // Apply current theme colours to per-row elements
        val primary = ThemeManager.getPalette(h.itemView.context).primary
        ThemeManager.tintProgressBar(h.progress, primary)
        h.saved.setTextColor(primary)
        h.percent.setTextColor(primary)
        ThemeManager.tintBackground(h.btnAdd, primary)
    }

    fun update(newGoals: List<SavingsGoalEntity>) {
        goals.clear(); goals.addAll(newGoals); notifyDataSetChanged()
    }
}

class SavingsGoalsActivity : BaseThemedActivity() {

    /* ═══════════════════════════════════════════════════════════════
       SECTION 2 — SAVINGS HOME
       ═══════════════════════════════════════════════════════════════ */
    // Main screen for tracking all active and completed savings goals.
    override fun themedBackgroundViewIds() = emptyList<Int>()
    override fun themedImageViewIds()      = listOf(R.id.btn_add_goal)
    override fun themedCardViewIds()       = listOf(R.id.card_summary)


    private lateinit var repo: BudgetRepository
    private lateinit var adapter: GoalAdapter
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_savings_goals)
        applyCurrentTheme()
        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        adapter = GoalAdapter(
            mutableListOf(),
            onLongPress = { goal ->
                lifecycleScope.launch {
                    if (userId != -1) {
                        repo.completeGoal(goal, userId)
                        runOnUiThread {
                            Toast.makeText(this@SavingsGoalsActivity,
                                "Goal completed! +200 XP 🎉", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onAddContribution = { goal -> showContributionDialog(goal) }
        )

        val rv = findViewById<RecyclerView>(R.id.rv_savings_goals)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        repo.getAllGoals(userId).observe(this) { goals -> adapter.update(goals) }

        repo.getTotalSaved(userId).observe(this) { total ->
            findViewById<TextView>(R.id.tv_total_saved).text =
                "R${"%,.0f".format(total ?: 0.0)}"
        }
        repo.countActiveGoals(userId).observe(this) { c ->
            findViewById<TextView>(R.id.tv_active_count).text = (c ?: 0).toString()
        }
        repo.countCompletedGoals(userId).observe(this) { c ->
            findViewById<TextView>(R.id.tv_completed_count).text = (c ?: 0).toString()
        }

        findViewById<ImageView>(R.id.btn_add_goal).setOnClickListener {
            startActivity(Intent(this, LogSavingsGoalActivity::class.java))
        }
    }

    private fun showContributionDialog(goal: SavingsGoalEntity) {
        /* ═══════════════════════════════════════════════════════════════
           SECTION 3 — CONTRIBUTIONS
           ═══════════════════════════════════════════════════════════════ */
        // Handles adding or withdrawing funds from a specific goal.
        val view  = LayoutInflater.from(this).inflate(R.layout.dialog_contribution, null)
        val etAmt = view.findViewById<EditText>(R.id.et_contribution_amount)
        val tvInfo= view.findViewById<TextView>(R.id.tv_contribution_info)
        val rgDir = view.findViewById<RadioGroup>(R.id.rg_contribution_direction)

        tvInfo.text = "Saved so far: R${"%,.0f".format(goal.savedAmount)}\n" +
                      "Still needed: R${"%,.0f".format((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0))}"

        AlertDialog.Builder(this, R.style.Style_ThemedDialog)
            .setTitle("💰 ${goal.name}")
            .setView(view)
            .setPositiveButton("Confirm") { _, _ ->
                val amount = etAmt.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val isAdding = rgDir.checkedRadioButtonId == R.id.rb_add

                lifecycleScope.launch {
                    if (isAdding) {
                        repo.savingsGoalDao.addContribution(goal.id, userId, amount)
                        repo.adjustOverallBudgetBalance(this@SavingsGoalsActivity, userId, -amount)
                        if (userId != -1) repo.addXp(userId, 5)
                        repo.addNotification(
                            userId     = userId,
                            icon       = "🎯",
                            title      = "Contribution added",
                            body       = "+R${"%,.0f".format(amount)} saved towards ${goal.name}",
                            time       = SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(Date()),
                            tag        = "NUDGE",
                            groupLabel = "Today"
                        )
                        val updated = repo.savingsGoalDao.getById(goal.id, userId)
                        if (updated != null && updated.savedAmount >= updated.targetAmount) {
                            repo.completeGoal(updated, userId)
                            runOnUiThread {
                                Toast.makeText(this@SavingsGoalsActivity,
                                    "🎉 Goal reached! +200 XP", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@SavingsGoalsActivity,
                                    "+R${"%,.0f".format(amount)} saved! +5 XP ⭐", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val withdraw = amount.coerceAtMost(goal.savedAmount)
                        repo.savingsGoalDao.addContribution(goal.id, userId, -withdraw)
                        repo.adjustOverallBudgetBalance(this@SavingsGoalsActivity, userId, withdraw)
                        repo.addNotification(
                            userId     = userId,
                            icon       = "↩️",
                            title      = "Amount withdrawn",
                            body       = "R${"%,.0f".format(withdraw)} returned from ${goal.name} to your budget",
                            time       = SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(Date()),
                            tag        = "NUDGE",
                            groupLabel = "Today"
                        )
                        runOnUiThread {
                            Toast.makeText(this@SavingsGoalsActivity,
                                "R${"%,.0f".format(withdraw)} returned to budget", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
