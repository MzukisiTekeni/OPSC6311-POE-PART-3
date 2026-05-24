package com.budgetbuddy.app

import android.os.Bundle
import android.view.*
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.ExpenseEntity
import com.budgetbuddy.app.db.SessionManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Group expenses by category for display
data class ExpenseGroup(
    val emoji: String, val category: String, val total: Double,
    val items: List<ExpenseEntity>, var isExpanded: Boolean = false
)

class ExpenseGroupAdapter(private val groups: MutableList<ExpenseGroup>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object { const val HEADER = 0; const val CHILD = 1 }

    data class Row(val group: ExpenseGroup, val item: ExpenseEntity? = null)

    private fun rows(): List<Row> = buildList {
        groups.forEach { g ->
            add(Row(g))
            if (g.isExpanded) g.items.forEach { add(Row(g, it)) }
        }
    }

    inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView    = v.findViewById(R.id.tv_group_emoji)
        val cat: TextView      = v.findViewById(R.id.tv_group_category)
        val total: TextView    = v.findViewById(R.id.tv_group_total)
        val chevron: ImageView = v.findViewById(R.id.iv_group_chevron)
    }
    inner class ChildVH(v: View) : RecyclerView.ViewHolder(v) {
        val desc: TextView = v.findViewById(R.id.tv_sub_description)
    }

    override fun getItemViewType(p: Int) = if (rows()[p].item == null) HEADER else CHILD
    override fun getItemCount() = rows().size

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (vt == HEADER)
            HeaderVH(inf.inflate(R.layout.item_expense_group_header, parent, false))
        else
            ChildVH(inf.inflate(R.layout.item_expense_sub_item, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val row = rows()[pos]
        when (holder) {
            is HeaderVH -> {
                holder.emoji.text   = row.group.emoji
                holder.cat.text     = row.group.category
                holder.total.text   = "R${"%,.2f".format(row.group.total)}"
                holder.chevron.setImageResource(
                    if (row.group.isExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right
                )
                holder.itemView.setOnClickListener {
                    row.group.isExpanded = !row.group.isExpanded
                    notifyDataSetChanged()
                }
            }
            is ChildVH -> {
                val exp = row.item!!
                holder.desc.text = "${exp.description.ifEmpty { exp.categoryName }} — R${"%,.2f".format(exp.amount)}"
            }
        }
    }

    fun updateData(newGroups: List<ExpenseGroup>) {
        groups.clear(); groups.addAll(newGroups); notifyDataSetChanged()
    }
}

class ViewExpensesActivity : BaseThemedActivity() {

    private lateinit var repo: BudgetRepository
    private lateinit var groupAdapter: ExpenseGroupAdapter
    private var currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_expenses)
        applyCurrentTheme()
        repo = BudgetRepository(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "View All Expenses"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        // Show current month label
        val displayMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        findViewById<TextView>(R.id.tv_selected_month).text = displayMonth

        groupAdapter = ExpenseGroupAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.rv_expenses).apply {
            layoutManager = LinearLayoutManager(this@ViewExpensesActivity)
            adapter        = groupAdapter
        }

        setupChips()
        observeExpenses(currentMonth)
    }

    private fun observeExpenses(month: String) {
        val userId = SessionManager.getUserId(this)
        repo.getExpensesByMonth(userId, month).observe(this) { expenses ->
            // Group by category
            val grouped = expenses
                .groupBy { it.categoryName }
                .map { (catName, items) ->
                    ExpenseGroup(
                        emoji    = items.first().categoryEmoji,
                        category = catName,
                        total    = items.sumOf { it.amount },
                        items    = items
                    )
                }
            groupAdapter.updateData(grouped)

            val total = expenses.sumOf { it.amount }
            findViewById<TextView>(R.id.tv_total_spent).text = "R${"%,.2f".format(total)}"
        }
    }

    private fun setupChips() {
        val chips = mapOf(
            R.id.chip_daily   to "Daily",
            R.id.chip_weekly  to "Weekly",
            R.id.chip_monthly to "Monthly",
            R.id.chip_yearly  to "Yearly"
        )
        chips.forEach { (id, label) ->
            findViewById<TextView>(id).setOnClickListener {
                chips.keys.forEach { cid ->
                    findViewById<TextView>(cid).apply {
                        setBackgroundResource(R.drawable.bg_chip_unselected)
                        setTextColor(getColor(R.color.text_secondary))
                    }
                }
                val primary = ThemeManager.getPalette(this).primary
                findViewById<TextView>(id).apply {
                    ThemeManager.tintBackground(this, primary)
                    setTextColor(getColor(R.color.text_on_primary))
                }
                // Reload expenses for the selected period
                val now   = LocalDate.now()
                val month = when (label) {
                    "Daily"   -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    "Weekly"  -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    "Monthly" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    "Yearly"  -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    else      -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                }
                observeExpenses(month)
            }
        }

        // Apply theme to the initially-selected chip (Monthly)
        val primary = ThemeManager.getPalette(this).primary
        ThemeManager.tintBackground(findViewById(R.id.chip_monthly), primary)
        findViewById<TextView>(R.id.chip_monthly).setTextColor(getColor(R.color.text_on_primary))
    }
}
