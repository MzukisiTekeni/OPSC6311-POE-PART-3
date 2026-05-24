package com.budgetbuddy.app

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Adapter ───────────────────────────────────────────────────────────────────
class HealthRowAdapter(private val rows: MutableList<HealthRow>) :
    RecyclerView.Adapter<HealthRowAdapter.VH>() {

    data class HealthRow(
        val emoji: String, val category: String,
        val spent: Double, val budget: Double
    ) {
        val pct: Int get() = if (budget > 0) ((spent / budget) * 100).toInt().coerceIn(0, 200) else 0
        val isOver: Boolean get() = spent > budget
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView    = v.findViewById(R.id.tv_health_emoji)
        val cat: TextView      = v.findViewById(R.id.tv_health_category)
        val spent: TextView    = v.findViewById(R.id.tv_health_spent)
        val pct: TextView      = v.findViewById(R.id.tv_health_percent)
        val bar: ProgressBar   = v.findViewById(R.id.progress_category_health)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_category_health_row, p, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val r = rows[pos]
        h.emoji.text = r.emoji
        h.cat.text   = r.category
        h.spent.text = "R${"%,.0f".format(r.spent)} of R${"%,.0f".format(r.budget)}"
        h.pct.text   = "${r.pct}%"
        h.pct.setTextColor(
            if (r.isOver) h.itemView.context.getColor(R.color.goal_red)
            else ThemeManager.getPalette(h.itemView.context).primary
        )
        h.bar.max      = 100
        h.bar.progress = r.pct.coerceAtMost(100)
        h.bar.progressDrawable = h.itemView.context.getDrawable(
            if (r.isOver) R.drawable.progress_bar_red else R.drawable.progress_bar_green
        )
    }

    fun update(newRows: List<HealthRow>) { rows.clear(); rows.addAll(newRows); notifyDataSetChanged() }
}

// ── Circular Ring View ────────────────────────────────────────────────────────

class DonutScoreView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private var score = 0

    private val paintTrack = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style  = android.graphics.Paint.Style.STROKE
        // color set in init block (context-aware)
        strokeWidth = 0f   // set in onSizeChanged
    }
    private val paintArc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style        = android.graphics.Paint.Style.STROKE
        strokeCap    = android.graphics.Paint.Cap.ROUND
        strokeWidth  = 0f
    }
    private val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        // color set in init block (context-aware)
    }
    private val paintSub = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        // color set in init block (context-aware)
    }

    init {
        val isDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        paintTrack.color = if (isDark) 0xFF2E3348.toInt() else 0xFFE8EDF3.toInt()
        paintText.color  = if (isDark) 0xFFF0F4F8.toInt() else 0xFF1A1A2E.toInt()
        paintSub.color   = if (isDark) 0xFF9BA3B8.toInt() else 0xFF6B7280.toInt()
    }

    private val oval = android.graphics.RectF()

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        val stroke = w * 0.10f
        paintTrack.strokeWidth = stroke
        paintArc.strokeWidth   = stroke
        val half = stroke / 2f
        oval.set(half, half, w - half, h - half)
        paintText.textSize = w * 0.22f
        paintSub.textSize  = w * 0.10f
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        // Track ring
        canvas.drawArc(oval, -90f, 360f, false, paintTrack)

        // Coloured arc
        val sweepAngle = (score / 100f * 360f)
        val arcColor = when {
            score >= 80 -> Color.parseColor("#22C55E")  // green
            score >= 55 -> Color.parseColor("#F59E0B")  // amber
            else        -> Color.parseColor("#EF4444")  // red
        }
        paintArc.color = arcColor
        canvas.drawArc(oval, -90f, sweepAngle, false, paintArc)

        // Score text
        val cx = oval.centerX()
        val cy = oval.centerY() - (paintText.ascent() + paintText.descent()) / 2f
        canvas.drawText(score.toString(), cx, cy, paintText)
        canvas.drawText("/100", cx, cy + paintText.textSize * 0.90f, paintSub)
    }

    fun setScore(value: Int) {
        score = value.coerceIn(0, 100)
        invalidate()
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────
class BudgetHealthActivity : BaseThemedActivity() {

    override fun themedTextViewIds() = listOf(R.id.tv_health_label)


    private lateinit var repo: BudgetRepository
    private lateinit var adapter: HealthRowAdapter
    private var userId = -1
    private val month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_health)
        applyCurrentTheme()
        repo   = BudgetRepository(this)
        userId = com.budgetbuddy.app.db.SessionManager.getUserId(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "Budget Health"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        adapter = HealthRowAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.rv_category_breakdown).apply {
            layoutManager = LinearLayoutManager(this@BudgetHealthActivity)
            adapter        = this@BudgetHealthActivity.adapter
        }

        observeData()
    }

    private fun observeData() {
        repo.getSpendingByCategory(userId, month).observe(this) { spendingList ->
            repo.getBudgetsByMonth(userId, month).observe(this) { budgetList ->
                val budgetMap    = budgetList.associateBy { it.categoryName }

                // ── Overall budget from SharedPreferences ─────────────────────
                val overallBudget     = repo.loadOverallBudget(this, userId)
                val totalCatBudgets   = budgetList.sumOf { it.amount }
                val totalSpent        = spendingList.sumOf { it.total }

                val rows = spendingList.map { s ->
                    val budget = budgetMap[s.categoryName]?.amount ?: 0.0
                    HealthRowAdapter.HealthRow(s.categoryEmoji, s.categoryName, s.total, budget)
                }

                adapter.update(rows)

                // ── Score: penalise category overspend AND overall overspend ──
                val score = computeScore(rows, overallBudget, totalSpent)
                findViewById<DonutScoreView>(R.id.donut_health_score).setScore(score)
                val labelRes = when {
                    score >= 80 -> "Great 🟢"
                    score >= 55 -> "Fair 🟡"
                    else        -> "At Risk 🔴"
                }
                findViewById<TextView>(R.id.tv_health_label).text = labelRes

                val onTrack   = rows.count { !it.isOver }
                val overspent = rows.count { it.isOver }
                lifecycleScope.launch {
                    val daysTracked = repo.getDaysTracked(userId, month)
                    runOnUiThread {
                        findViewById<TextView>(R.id.tv_days_tracked).text = daysTracked.toString()
                        findViewById<TextView>(R.id.tv_on_track).text     = onTrack.toString()
                        findViewById<TextView>(R.id.tv_overspent).text    = overspent.toString()
                    }
                }

                // ── Build a rich, multi-section summary ───────────────────────
                val summaryParts = mutableListOf<String>()

                // 1. Category budgets exceed the overall budget
                if (overallBudget > 0 && totalCatBudgets > overallBudget) {
                    val excess = totalCatBudgets - overallBudget
                    val catTotalStr = "%,.0f".format(totalCatBudgets)
                    val overallStr = "%,.0f".format(overallBudget)
                    val excessStr = "%,.0f".format(excess)
                    summaryParts.add(
                        "⚠️ Your category budgets total R$catTotalStr, " +
                        "which exceeds your overall monthly budget of R$overallStr " +
                        "by R$excessStr. Consider reducing some category limits."
                    )
                }

                // 2. Total spending exceeds overall budget
                if (overallBudget > 0 && totalSpent > overallBudget) {
                    val over = totalSpent - overallBudget
                    val totalSpentStr = "%,.0f".format(totalSpent)
                    val overStr = "%,.0f".format(over)
                    summaryParts.add(
                        "🚨 Total spending (R$totalSpentStr) has exceeded your " +
                        "overall monthly budget by R$overStr."
                    )
                } else if (overallBudget > 0) {
                    val remaining = overallBudget - totalSpent
                    val usedPct   = (totalSpent / overallBudget * 100).toInt()
                    val totalSpentStr = "%,.0f".format(totalSpent)
                    val overallStr = "%,.0f".format(overallBudget)
                    val remainingStr = "%,.0f".format(remaining)
                    summaryParts.add(
                        "Overall: R$totalSpentStr spent of " +
                        "R$overallStr ($usedPct%). " +
                        "R$remainingStr remaining this month."
                    )
                }

                // 3. Per-category overspending
                val overRows = rows.filter { it.isOver }
                if (overRows.isNotEmpty()) {
                    val parts = overRows.joinToString(", ") {
                        val overAmount = it.spent - it.budget
                        val overAmountStr = "%,.0f".format(overAmount)
                        "${it.category} by R$overAmountStr"
                    }
                    val totalOver = overRows.sumOf { it.spent - it.budget }
                    val totalOverStr = "%,.0f".format(totalOver)
                    summaryParts.add(
                        "Over category budget on $parts — " +
                        "R$totalOverStr in unplanned spending."
                    )
                } else if (rows.isNotEmpty()) {
                    summaryParts.add("All categories are within their individual budgets this month. 🎉")
                }

                if (summaryParts.isEmpty()) {
                    summaryParts.add("No expenses recorded yet. Start logging to see your health score.")
                }

                findViewById<TextView>(R.id.tv_overspend_summary).text =
                    summaryParts.joinToString("\n\n")
            }
        }
    }

    private fun computeScore(
        rows: List<HealthRowAdapter.HealthRow>,
        overallBudget: Double,
        totalSpent: Double
    ): Int {
        var score = 100

        // Deduct for each category that is over its own budget
        rows.filter { it.isOver }.forEach { r ->
            val overPct = if (r.budget > 0) ((r.spent - r.budget) / r.budget * 100).toInt() else 50
            score -= (10 + overPct / 10).coerceAtMost(25)
        }

        // Deduct for total spending exceeding the overall budget
        if (overallBudget > 0 && totalSpent > overallBudget) {
            val overallOverPct = ((totalSpent - overallBudget) / overallBudget * 100).toInt()
            score -= (15 + overallOverPct / 5).coerceAtMost(30)
        }

        return score.coerceAtLeast(0)
    }
}
