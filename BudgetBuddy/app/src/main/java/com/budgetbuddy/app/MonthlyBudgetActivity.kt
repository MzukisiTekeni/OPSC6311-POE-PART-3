package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetEntity
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

// ── Adapter ───────────────────────────────────────────────────────────────────
class BudgetRowAdapter(
    private val items: List<BudgetEntity>,
    private val onEdit: (BudgetEntity) -> Unit
) : RecyclerView.Adapter<BudgetRowAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView = v.findViewById(R.id.tv_budget_emoji)
        val name: TextView  = v.findViewById(R.id.tv_budget_category_name)
        val amt: TextView   = v.findViewById(R.id.tv_budget_amount_value)
        val edit: ImageView = v.findViewById(R.id.iv_edit_budget)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_budget_row, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val b = items[pos]
        h.emoji.text = b.categoryEmoji
        h.name.text  = b.categoryName
        h.amt.text   = "R${"%,.2f".format(b.amount)}"
        h.edit.setOnClickListener { onEdit(b) }

        // Apply current theme colour to the budget amount text
        val primary = ThemeManager.getPalette(h.itemView.context).primary
        h.amt.setTextColor(primary)
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────
class MonthlyBudgetActivity : BaseThemedActivity() {

    override fun themedBackgroundViewIds() = emptyList<Int>()
    override fun themedImageViewIds()      = listOf(R.id.btn_create_budget)
    override fun themedCardViewIds()       = listOf(R.id.card_overall_budget)


    private lateinit var repo: BudgetRepository
    private var userId = -1
    private val month  = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_budget)
        applyCurrentTheme()
        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "Monthly Budget"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        setupOverallBudgetCard()
        setupCategoryBudgetList()
        checkCategoriesWithoutBudgets()
    }

    // ── Overall budget card ───────────────────────────────────────────────────
    private fun setupOverallBudgetCard() {
        val tvOverall = findViewById<TextView>(R.id.tv_overall_budget_amount)
        // Returns 0.0 by default — shows "Tap to set" until user enters a value
        val current   = repo.loadOverallBudget(this, userId)
        tvOverall.text = if (current > 0) "R${"%,.2f".format(current)}" else "Tap to set →"

        findViewById<View>(R.id.card_overall_budget).setOnClickListener {
            showSetOverallBudgetDialog()
        }
    }

    private fun showSetOverallBudgetDialog() {
        val view    = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val etAmt   = view.findViewById<EditText>(R.id.et_dialog_amount)
        val current = repo.loadOverallBudget(this, userId)
        if (current > 0) etAmt.setText(current.toBigDecimal().stripTrailingZeros().toPlainString())

        AlertDialog.Builder(this)
            .setTitle("💰 Set Overall Monthly Budget")
            .setMessage("This is the total amount you plan to spend this month. It appears on your Dashboard.")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val amount = etAmt.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                repo.saveOverallBudget(this, userId, amount)
                val tvOverall = findViewById<TextView>(R.id.tv_overall_budget_amount)
                tvOverall.text = "R${"%,.2f".format(amount)}"
                Toast.makeText(this, "Overall budget saved ✓", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    repo.addNotification(
                        userId     = userId,
                        icon       = "💰",
                        title      = "Overall budget set",
                        body       = "Your monthly budget is R${"%,.2f".format(amount)}",
                        time       = SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(Date()),
                        tag        = "INSIGHT",
                        groupLabel = "Today"
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Category budget list ──────────────────────────────────────────────────
    private fun setupCategoryBudgetList() {
        val rv = findViewById<RecyclerView>(R.id.rv_budget_items)
        rv.layoutManager = LinearLayoutManager(this)

        repo.getBudgetsByMonth(userId, month).observe(this) { budgets ->
            rv.adapter = BudgetRowAdapter(budgets) { budget ->
                showEditCategoryBudgetDialog(budget)
            }
        }

        repo.getTotalBudget(userId, month).observe(this) { total ->
            findViewById<TextView>(R.id.tv_total_budget).text =
                "R${"%,.2f".format(total ?: 0.0)}"
        }

        findViewById<ImageView>(R.id.btn_create_budget).setOnClickListener {
            startActivity(Intent(this, LogBudgetActivity::class.java))
        }
    }

    private fun showEditCategoryBudgetDialog(budget: BudgetEntity) {
        val view  = LayoutInflater.from(this).inflate(R.layout.dialog_set_budget, null)
        val etAmt = view.findViewById<EditText>(R.id.et_dialog_amount)
        etAmt.setText(budget.amount.toBigDecimal().stripTrailingZeros().toPlainString())

        AlertDialog.Builder(this)
            .setTitle("${budget.categoryEmoji} ${budget.categoryName}")
            .setMessage("Update the monthly budget limit for this category.")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val amount = etAmt.text.toString().toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    repo.budgetDao.insert(budget.copy(amount = amount))
                    runOnUiThread {
                        Toast.makeText(this@MonthlyBudgetActivity,
                            "${budget.categoryEmoji} budget updated ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Categories with no budget → notify ───────────────────────────────────
    private fun checkCategoriesWithoutBudgets() {
        lifecycleScope.launch {
            val allCats   = repo.getActiveCategoriesNow(userId)
            val budgets   = repo.budgetDao.getByMonthNow(userId, month)
            val budgetIds = budgets.map { it.categoryId }.toSet()
            val missing   = allCats.filter { it.id !in budgetIds }
            missing.forEach { cat ->
                repo.addNotification(
                    userId     = userId,
                    icon       = "⚠️",
                    title      = "Budget missing: ${cat.name}",
                    body       = "No budget set for ${cat.emoji} ${cat.name} this month.",
                    time       = SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(Date()),
                    tag        = "ALERT",
                    groupLabel = "Reminders"
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupOverallBudgetCard()
        checkCategoriesWithoutBudgets()
    }
}
