package com.budgetbuddy.app

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Bar Chart View ─────────────────────────────────────────────────────────────
class SpendingBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Bar(val label: String, val spent: Double, val budget: Double)

    private var bars = emptyList<Bar>()

    private val paintSpent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1"); style = Paint.Style.FILL
    }
    private val paintBudget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E7FF"); style = Paint.Style.FILL
    }
    private val paintOver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444"); style = Paint.Style.FILL
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // color set in setData() using context
        textAlign = Paint.Align.CENTER; textSize = 28f
    }
    private val paintAmount = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // color set in setData() using context
        textAlign = Paint.Align.CENTER
        textSize = 22f; typeface = Typeface.DEFAULT_BOLD
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // color set in setData() using context
        strokeWidth = 1f
    }
    private val paintLegendBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var contextRef: android.content.Context? = null

    fun setData(data: List<Bar>, ctx: android.content.Context? = null) {
        bars = data
        if (ctx != null) {
            contextRef = ctx
            val isDark = (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            paintAmount.color = if (isDark) 0xFFF0F4F8.toInt() else 0xFF1A1A2E.toInt()
            paintLabel.color  = if (isDark) 0xFF9BA3B8.toInt() else 0xFF6B7280.toInt()
            paintGrid.color   = if (isDark) 0xFF2E3348.toInt() else 0xFFE5E7EB.toInt()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) {
            paintLabel.textAlign = Paint.Align.CENTER
            canvas.drawText("No data for this period", width / 2f, height / 2f, paintLabel)
            return
        }

        val padL    = 32f
        val padR    = 20f
        val padTop  = 28f
        // Extra bottom padding: 36px for x-labels + 16px gap + 24px legend row + 12px margin
        val padBot  = 88f
        val chartW  = width  - padL - padR
        val chartH  = height - padTop - padBot

        val maxVal = bars.maxOf { maxOf(it.spent, it.budget) }.let { if (it == 0.0) 1.0 else it }

        // Horizontal grid lines
        for (i in 0..4) {
            val y = padTop + chartH - (i / 4f) * chartH
            canvas.drawLine(padL, y, padL + chartW, y, paintGrid)
        }

        val groupW = chartW / bars.size
        val barW   = groupW * 0.33f
        val gap    = groupW * 0.04f

        bars.forEachIndexed { idx, bar ->
            val groupX = padL + idx * groupW + groupW / 2f

            // Budget bar
            val budgetH = (bar.budget / maxVal * chartH).toFloat()
            val bLeft   = groupX - barW - gap / 2f
            val bTop    = padTop + chartH - budgetH
            canvas.drawRoundRect(bLeft, bTop, bLeft + barW, padTop + chartH, 8f, 8f, paintBudget)

            // Spent bar
            val spentH    = (bar.spent / maxVal * chartH).toFloat().coerceAtMost(chartH.toFloat())
            val sLeft     = groupX + gap / 2f
            val sTop      = padTop + chartH - spentH
            val overPaint = if (bar.spent > bar.budget) paintOver else paintSpent
            canvas.drawRoundRect(sLeft, sTop, sLeft + barW, padTop + chartH, 8f, 8f, overPaint)

            // Amount label above spent bar (only if bar has height)
            if (bar.spent > 0) {
                val amtText = "R${"%,.0f".format(bar.spent)}"
                canvas.drawText(amtText, sLeft + barW / 2f,
                    (sTop - 8f).coerceAtLeast(padTop + 14f), paintAmount)
            }

            // X-axis label — sits below the chart with proper vertical spacing
            canvas.drawText(bar.label, groupX, padTop + chartH + 34f, paintLabel)
        }

        // ── Legend row — sits below the x-axis labels with horizontal spacing ──
        val legendY   = padTop + chartH + 70f   // 36px below x-axis baseline
        val boxSize   = 14f
        val textGap   = 6f
        val legendGap = 48f                      // horizontal gap between the two items

        // Measure text widths to centre the pair
        paintLabel.textAlign = Paint.Align.LEFT
        val budgetTextW = paintLabel.measureText("Budget")
        val spentTextW  = paintLabel.measureText("Spent")
        val totalW      = boxSize + textGap + budgetTextW + legendGap + boxSize + textGap + spentTextW
        var lx          = (width - totalW) / 2f

        // Budget box + label
        paintLegendBox.color = paintBudget.color
        canvas.drawRoundRect(lx, legendY - boxSize, lx + boxSize, legendY, 3f, 3f, paintLegendBox)
        canvas.drawText("Budget", lx + boxSize + textGap, legendY, paintLabel)
        lx += boxSize + textGap + budgetTextW + legendGap

        // Spent box + label
        paintLegendBox.color = paintSpent.color
        canvas.drawRoundRect(lx, legendY - boxSize, lx + boxSize, legendY, 3f, 3f, paintLegendBox)
        canvas.drawText("Spent", lx + boxSize + textGap, legendY, paintLabel)

        paintLabel.textAlign = Paint.Align.CENTER  // restore default
    }
}

// ── Insight Adapter ───────────────────────────────────────────────────────────
class InsightAdapter(private val items: MutableList<Pair<String, String>>) :
    RecyclerView.Adapter<InsightAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView = v.findViewById(R.id.tv_insight_emoji)
        val text: TextView  = v.findViewById(R.id.tv_insight_text)
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_insight_row, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.emoji.text = items[pos].first; h.text.text = items[pos].second
    }

    fun update(newItems: List<Pair<String, String>>) {
        items.clear(); items.addAll(newItems); notifyDataSetChanged()
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────
class StatisticsActivity : BaseThemedActivity() {

    override fun onResume() {
        super.onResume()  // calls applyCurrentTheme()
        // Re-tint whichever chip is currently selected
        val chipIds = mapOf("Week" to R.id.chip_week, "Month" to R.id.chip_month,
            "3 Months" to R.id.chip_3months, "Year" to R.id.chip_year)
        chipIds[currentPeriod]?.let { id ->
            runCatching { ThemeManager.tintBackground(findViewById(id), ThemeManager.getPalette(this).primary) }
        }
    }


    private lateinit var repo: BudgetRepository
    private lateinit var insightAdapter: InsightAdapter
    private var currentPeriod = "Week"
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)
        applyCurrentTheme()
        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "Statistics"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        insightAdapter = InsightAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.rv_insights).apply {
            layoutManager = LinearLayoutManager(this@StatisticsActivity)
            adapter        = insightAdapter
        }

        setupChips()
        loadStatsFor("Week")
        // Tint initial selected chip with current theme
        ThemeManager.tintBackground(findViewById(R.id.chip_week), ThemeManager.getPalette(this).primary)
    }

    private fun setupChips() {
        val chips = mapOf(
            R.id.chip_week    to "Week",
            R.id.chip_month   to "Month",
            R.id.chip_3months to "3 Months",
            R.id.chip_year    to "Year"
        )
        chips.forEach { (id, label) ->
            findViewById<TextView>(id).setOnClickListener {
                currentPeriod = label
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
                loadStatsFor(label)
            }
        }
    }

    private fun loadStatsFor(period: String) {
        val now = LocalDate.now()
        val fromDate = when (period) {
            "Week"     -> now.minusDays(6)
            "Month"    -> now.withDayOfMonth(1)
            "3 Months" -> now.minusMonths(3).withDayOfMonth(1)
            "Year"     -> now.withDayOfYear(1)
            else       -> now.minusDays(6)
        }.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        lifecycleScope.launch {
            val total = repo.expenseDao.getTotalSince(userId, fromDate)

            val days = when (period) {
                "Week"     -> 7L
                "Month"    -> now.dayOfMonth.toLong()
                "3 Months" -> 90L
                "Year"     -> now.dayOfYear.toLong()
                else       -> 7L
            }
            val daily = if (days > 0) total / days else 0.0

            val currentMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val spending     = repo.expenseDao.getSpendingByCategoryForMonthNow(userId, currentMonth)
            val budgets      = repo.budgetDao.getByMonthNow(userId, currentMonth)
            val budgetMap    = budgets.associateBy { it.categoryName }
            val topCategory  = spending.maxByOrNull { it.total }?.categoryName ?: "–"

            // Bar chart data
            val barData = spending.take(6).map { s ->
                val budget     = budgetMap[s.categoryName]?.amount ?: 0.0
                val shortLabel = s.categoryEmoji.ifEmpty { s.categoryName.take(4) }
                SpendingBarChartView.Bar(shortLabel, s.total, budget)
            }
            val finalBarData: List<SpendingBarChartView.Bar> = if (period == "Week") {
                val dailyTotals = repo.expenseDao.getDailyTotals(userId, fromDate)
                val dayNames    = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val dayMap      = dailyTotals.associateBy { it.date }
                (0..6).map { offset ->
                    val date     = now.minusDays((6 - offset).toLong())
                    val dateStr  = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val spentAmt = dayMap[dateStr]?.total ?: 0.0
                    val dayBudget = repo.budgetDao.getTotalBudgetForMonthNow(userId, currentMonth) / 30.0
                    SpendingBarChartView.Bar(dayNames[date.dayOfWeek.value - 1], spentAmt, dayBudget)
                }
            } else barData

            // vs previous period
            val prevFromDate = when (period) {
                "Week"     -> now.minusDays(13)
                "Month"    -> now.minusMonths(1).withDayOfMonth(1)
                "3 Months" -> now.minusMonths(6).withDayOfMonth(1)
                "Year"     -> now.minusYears(1).withDayOfYear(1)
                else       -> now.minusDays(13)
            }.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val prevTotal = repo.expenseDao.getTotalSince(userId, prevFromDate)
            val vsLastPct = if (prevTotal > 0) ((total - prevTotal) / prevTotal * 100).toInt() else 0
            val vsText    = if (vsLastPct >= 0) "+$vsLastPct%" else "$vsLastPct%"

            // ── Load overall budget for cross-checks ─────────────────────
            val overallBudget   = repo.loadOverallBudget(this@StatisticsActivity, userId)
            val totalCatBudgets = budgets.sumOf { it.amount }

            // Insights
            val insights = mutableListOf<Pair<String, String>>()

            if (total == 0.0) {
                insights.add("💡" to "No expenses recorded for this period yet. Start logging to see insights.")
            } else {
                // 1. Overall budget vs total spending
                if (overallBudget > 0) {
                    when {
                        total > overallBudget -> {
                            val over = total - overallBudget
                            insights.add("🚨" to "You have exceeded your overall monthly budget " +
                                "(R${"%,.0f".format(overallBudget)}) by R${"%,.0f".format(over)}. " +
                                "Review your spending immediately.")
                        }
                        total >= overallBudget * 0.90 -> {
                            val remaining = overallBudget - total
                            insights.add("⚠️" to "You have used ${((total/overallBudget)*100).toInt()}% of your monthly budget. " +
                                "Only R${"%,.0f".format(remaining)} remaining — spend carefully.")
                        }
                        else -> {
                            val remaining = overallBudget - total
                            val usedPct   = ((total / overallBudget) * 100).toInt()
                            insights.add("✅" to "You have used $usedPct% of your monthly budget. " +
                                "R${"%,.0f".format(remaining)} still available.")
                        }
                    }
                }

                // 2. Category budgets sum vs overall budget
                if (overallBudget > 0 && totalCatBudgets > overallBudget) {
                    val excess = totalCatBudgets - overallBudget
                    insights.add("⚠️" to "Your category budgets total R${"%,.0f".format(totalCatBudgets)}, " +
                        "exceeding your overall budget by R${"%,.0f".format(excess)}. " +
                        "Reduce some category limits on the Monthly Budget page.")
                }

                // 3. Per-category overspend
                val overBudgetCats = spending.filter { s ->
                    val b = budgetMap[s.categoryName]?.amount ?: 0.0; b > 0 && s.total > b
                }
                if (overBudgetCats.isNotEmpty()) {
                    val names = overBudgetCats.joinToString(", ") { it.categoryName }
                    val totalOver = overBudgetCats.sumOf { s ->
                        s.total - (budgetMap[s.categoryName]?.amount ?: 0.0)
                    }
                    insights.add("🔴" to "${overBudgetCats.size} ${if (overBudgetCats.size == 1) "category" else "categories"} " +
                        "over individual budget: $names. " +
                        "Total overspend: R${"%,.0f".format(totalOver)}.")
                } else if (spending.isNotEmpty() && budgets.isNotEmpty()) {
                    insights.add("🟢" to "All categories are within their individual budgets. Keep it up!")
                }

                // 4. Highest spend category
                if (spending.isNotEmpty()) {
                    val topCat = spending.first()
                    insights.add("📊" to "${topCat.categoryName} is your top spending category " +
                        "at R${"%,.0f".format(topCat.total)} this month.")
                }

                // 5. Daily average
                if (daily > 0) {
                    insights.add("📅" to "Your daily average spend is R${"%,.0f".format(daily)} " +
                        "for the selected period.")
                }

                // 6. Lowest spend category
                if (spending.size >= 2) {
                    val lowest = spending.last()
                    insights.add("💚" to "${lowest.categoryName} has your lowest spend " +
                        "at R${"%,.0f".format(lowest.total)} this month.")
                }

                // 7. vs previous period
                if (prevTotal > 0) {
                    val changeText = if (vsLastPct > 0)
                        "up $vsLastPct% compared to the previous period — consider cutting back."
                    else if (vsLastPct < 0)
                        "down ${-vsLastPct}% compared to the previous period. Great progress!"
                    else
                        "the same as the previous period."
                    insights.add((if (vsLastPct > 0) "📈" else "📉") to "Spending is $changeText")
                }
            }

            runOnUiThread {
                findViewById<TextView>(R.id.tv_total_spending).text = "R${"%,.0f".format(total)}"
                findViewById<TextView>(R.id.tv_daily_average).text  = "R${"%,.0f".format(daily)}"
                findViewById<TextView>(R.id.tv_top_category).text   = topCategory
                val vsTv = findViewById<TextView>(R.id.tv_vs_last_period)
                vsTv.text = vsText
                vsTv.setTextColor(if (vsLastPct <= 0) ThemeManager.getPalette(this@StatisticsActivity).primary else getColor(R.color.goal_red))
                insightAdapter.update(insights)
                findViewById<SpendingBarChartView>(R.id.bar_chart).setData(finalBarData, this@StatisticsActivity)
            }
        }
    }
}
