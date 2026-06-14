package com.budgetbuddy.app

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Bar Chart View ─────────────────────────────────────────────────────────────
/* ═══════════════════════════════════════════════════════════════
   SECTION 1 — CUSTOM CUSTOM CHART VIEW
   ═══════════════════════════════════════════════════════════════ */
// Custom bar chart rendering code to visualize spending vs budget goals
class SpendingBarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Bar(
        val label: String,
        val spent: Double,
        val budget: Double,        // max goal
        val minGoal: Double = 0.0  // min goal
    )

    private var bars = emptyList<Bar>()
    private var showGoalLines = true

    private val paintSpent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1"); style = Paint.Style.FILL
    }
    private val paintBudget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E7FF"); style = Paint.Style.FILL
    }
    private val paintOver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444"); style = Paint.Style.FILL
    }
    private val paintUnder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 28f
    }
    private val paintAmount = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f; typeface = Typeface.DEFAULT_BOLD
    }
    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
    }
    private val paintMaxGoalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val paintMinGoalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val paintGoalLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f; textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD
    }
    private val paintLegendBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setData(data: List<Bar>, ctx: Context? = null, showGoals: Boolean = true) {
        bars = data
        showGoalLines = showGoals
        if (ctx != null) {
            val isDark = (ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            paintAmount.color = if (isDark) 0xFFF0F4F8.toInt() else 0xFF1A1A2E.toInt()
            paintLabel.color  = if (isDark) 0xFF9BA3B8.toInt() else 0xFF6B7280.toInt()
            paintGrid.color   = if (isDark) 0xFF2E3348.toInt() else 0xFFE5E7EB.toInt()
            paintGoalLabel.color = if (isDark) 0xFF9BA3B8.toInt() else 0xFF6B7280.toInt()
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
        // extra room for 2-line x labels + 2 legend rows
        val padBot  = 148f
        val chartW  = width  - padL - padR
        val chartH  = height - padTop - padBot

        val maxVal = bars.maxOf { maxOf(it.spent, it.budget, it.minGoal) }
            .let { if (it == 0.0) 1.0 else it }

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

            // Budget (max goal) bar — background
            val budgetH = (bar.budget / maxVal * chartH).toFloat()
            val bLeft   = groupX - barW - gap / 2f
            val bTop    = padTop + chartH - budgetH
            if (bar.budget > 0) {
                canvas.drawRoundRect(bLeft, bTop, bLeft + barW, padTop + chartH, 8f, 8f, paintBudget)
            }

            // Spent bar — colour based on goal zone
            val spentH    = (bar.spent / maxVal * chartH).toFloat().coerceAtMost(chartH.toFloat())
            val sLeft     = if (bar.budget > 0) groupX + gap / 2f else groupX - barW / 2f
            val sTop      = padTop + chartH - spentH
            val barPaint = when {
                bar.spent > bar.budget && bar.budget > 0 -> paintOver
                bar.minGoal > 0 && bar.spent < bar.minGoal -> paintUnder
                else -> paintSpent
            }
            if (bar.spent > 0) {
                canvas.drawRoundRect(sLeft, sTop, sLeft + barW, padTop + chartH, 8f, 8f, barPaint)
            }

            // Amount label above spent bar
            if (bar.spent > 0) {
                val amtText = "R${"%,.0f".format(bar.spent)}"
                canvas.drawText(amtText, sLeft + barW / 2f,
                    (sTop - 8f).coerceAtLeast(padTop + 14f), paintAmount)
            }

            // X-axis label — split "emoji name" into two lines for readability
            val labelParts = bar.label.split(" ", limit = 2)
            if (labelParts.size == 2) {
                canvas.drawText(labelParts[0], groupX, padTop + chartH + 30f, paintLabel)
                canvas.drawText(labelParts[1], groupX, padTop + chartH + 54f, paintLabel)
            } else {
                canvas.drawText(bar.label, groupX, padTop + chartH + 38f, paintLabel)
            }
        }

        // ── Draw min/max goal lines across the full chart width ──────────────
        if (showGoalLines) {
            val barsWithBudget = bars.filter { it.budget > 0 }
            val barsWithMin    = bars.filter { it.minGoal > 0 }

            // For category charts: use a single representative max/min line
            // derived from the total goals scaled to chart max, so lines are
            // always meaningful regardless of period
            val avgMaxGoal = if (barsWithBudget.isNotEmpty())
                barsWithBudget.map { it.budget }.average() else Double.NaN
            val avgMinGoal = if (barsWithMin.isNotEmpty())
                barsWithMin.map { it.minGoal }.average() else Double.NaN

            if (!avgMaxGoal.isNaN() && avgMaxGoal > 0) {
                val maxGoalY = padTop + chartH - (avgMaxGoal / maxVal * chartH).toFloat()
                canvas.drawLine(padL, maxGoalY, padL + chartW, maxGoalY, paintMaxGoalLine)
                paintGoalLabel.color = paintMaxGoalLine.color
                canvas.drawText("Max", padL + 4f, maxGoalY - 6f, paintGoalLabel)
            }

            if (!avgMinGoal.isNaN() && avgMinGoal > 0) {
                val minGoalY = padTop + chartH - (avgMinGoal / maxVal * chartH).toFloat()
                canvas.drawLine(padL, minGoalY, padL + chartW, minGoalY, paintMinGoalLine)
                paintGoalLabel.color = paintMinGoalLine.color
                canvas.drawText("Min", padL + 4f, minGoalY - 6f, paintGoalLabel)
            }
        }

        // ── Legend row 1: Budget / Spent / Over ─────────────────────────────
        val legendY1  = padTop + chartH + 96f
        val legendY2  = padTop + chartH + 124f
        val boxSize   = 14f
        val textGap   = 6f

        paintLabel.textAlign = Paint.Align.LEFT

        // Row 1
        var lx = padL
        paintLegendBox.color = paintBudget.color
        canvas.drawRoundRect(lx, legendY1 - boxSize, lx + boxSize, legendY1, 3f, 3f, paintLegendBox)
        canvas.drawText("Max Goal", lx + boxSize + textGap, legendY1, paintLabel)
        lx += boxSize + textGap + paintLabel.measureText("Max Goal") + 20f

        paintLegendBox.color = paintSpent.color
        canvas.drawRoundRect(lx, legendY1 - boxSize, lx + boxSize, legendY1, 3f, 3f, paintLegendBox)
        canvas.drawText("On Track", lx + boxSize + textGap, legendY1, paintLabel)

        // Row 2
        lx = padL
        paintLegendBox.color = paintOver.color
        canvas.drawRoundRect(lx, legendY2 - boxSize, lx + boxSize, legendY2, 3f, 3f, paintLegendBox)
        canvas.drawText("Over Max", lx + boxSize + textGap, legendY2, paintLabel)
        lx += boxSize + textGap + paintLabel.measureText("Over Max") + 20f

        paintLegendBox.color = paintUnder.color
        canvas.drawRoundRect(lx, legendY2 - boxSize, lx + boxSize, legendY2, 3f, 3f, paintLegendBox)
        canvas.drawText("Below Min", lx + boxSize + textGap, legendY2, paintLabel)

        paintLabel.textAlign = Paint.Align.CENTER
    }
}

// ── Insight Adapter ───────────────────────────────────────────────────────────
/* ═══════════════════════════════════════════════════════════════
   SECTION 2 — RECYCLERVIEW ADAPTER
   ═══════════════════════════════════════════════════════════════ */
// Adapter for displaying text-based insights in a list
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
/* ═══════════════════════════════════════════════════════════════
   SECTION 3 — STATISTICS HOME
   ═══════════════════════════════════════════════════════════════ */
// Controls time period filtering and populates chart and insights from DB
class StatisticsActivity : BaseThemedActivity() {

    override fun onResume() {
        super.onResume()
        val chipIds = mapOf(
            "Day"      to R.id.chip_day,
            "Week"     to R.id.chip_week,
            "Month"    to R.id.chip_month,
            "3 Months" to R.id.chip_3months,
            "Year"     to R.id.chip_year
        )
        chipIds[currentPeriod]?.let { id ->
            runCatching { ThemeManager.tintBackground(findViewById(id), ThemeManager.getPalette(this).primary) }
        }
    }

    private lateinit var repo: BudgetRepository
    private lateinit var insightAdapter: InsightAdapter
    private var currentPeriod = "Month"   // ← default is now Month
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
        // Load Month tab by default and highlight it
        loadStatsFor("Month")
        ThemeManager.tintBackground(findViewById(R.id.chip_month), ThemeManager.getPalette(this).primary)
        // Reset week chip to unselected style
        findViewById<TextView>(R.id.chip_week).apply {
            setBackgroundResource(R.drawable.bg_chip_unselected)
            setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun setupChips() {
        val chips = mapOf(
            R.id.chip_day      to "Day",
            R.id.chip_week     to "Week",
            R.id.chip_month    to "Month",
            R.id.chip_3months  to "3 Months",
            R.id.chip_year     to "Year"
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
            "Day"      -> now.toString()
            "Week"     -> now.minusDays(6)
            "Month"    -> now.withDayOfMonth(1)
            "3 Months" -> now.minusMonths(3).withDayOfMonth(1)
            "Year"     -> now.withDayOfYear(1)
            else       -> now.minusDays(6)
        }.let {
            if (it is LocalDate) it.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            else it as String
        }

        lifecycleScope.launch {
            val total = repo.expenseDao.getTotalSince(userId, fromDate)

            val days = when (period) {
                "Day"      -> 1L
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

            // Load min and max goals from SharedPreferences
            val maxGoal = repo.loadOverallBudget(this@StatisticsActivity, userId)
            val minGoal = repo.loadMinGoal(this@StatisticsActivity, userId)

            // ── Build category bar data (used for Month, 3M, Year, Week, Day) ──
            // Each bar = one category: budget bar (grey) + spent bar (coloured)
            // Min/max goal lines are drawn from the per-category proportional goals
            fun buildCategoryBars(spendMultiplier: Double = 1.0): List<SpendingBarChartView.Bar> {
                return spending.take(6).map { s ->
                    val catBudget  = budgetMap[s.categoryName]?.amount ?: 0.0
                    val emoji      = if (s.categoryEmoji.isNotEmpty()) s.categoryEmoji + " " else ""
                    val shortLabel = emoji + s.categoryName.take(6)
                    // For weekly/daily views, scale the monthly category budget proportionally
                    val scaledBudget = if (catBudget > 0 && spendMultiplier < 1.0)
                        catBudget * spendMultiplier else catBudget
                    val catMinGoal = if (maxGoal > 0 && catBudget > 0 && minGoal > 0)
                        minGoal * (catBudget / maxGoal) * (if (spendMultiplier < 1.0) spendMultiplier else 1.0)
                    else 0.0
                    SpendingBarChartView.Bar(shortLabel, s.total, scaledBudget, catMinGoal)
                }
            }

            // For daily tab: fetch only today's spending per category
            val todayStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val dailySpending = if (period == "Day") {
                repo.expenseDao.getDailyTotals(userId, todayStr)
            } else emptyList()

            val finalBarData: List<SpendingBarChartView.Bar> = when (period) {
                "Day" -> {
                    // Show category breakdown for today using getDailyTotals gives only
                    // date totals — so we use category spending filtered to today's date
                    // by querying getByMonthNow and summing same-day entries, or simply
                    // show category bars with today-proportional budget (1 day / month days)
                    val daysInMonth = now.lengthOfMonth().toDouble()
                    buildCategoryBars(spendMultiplier = 1.0 / daysInMonth)
                }
                "Week" -> {
                    // Show category breakdown for the week (more meaningful than 7 daily bars)
                    // Scale monthly budget by 7/daysInMonth to get weekly budget
                    val daysInMonth = now.lengthOfMonth().toDouble()
                    buildCategoryBars(spendMultiplier = 7.0 / daysInMonth)
                }
                else -> buildCategoryBars(spendMultiplier = 1.0)
            }

            // vs previous period
            val prevFromDate = when (period) {
                "Day"      -> now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "Week"     -> now.minusDays(13).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "Month"    -> now.minusMonths(1).withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "3 Months" -> now.minusMonths(6).withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "Year"     -> now.minusYears(1).withDayOfYear(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                else       -> now.minusDays(13).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            }
            val prevTotal = repo.expenseDao.getTotalSince(userId, prevFromDate)
            val vsLastPct = if (prevTotal > 0) ((total - prevTotal) / prevTotal * 100).toInt() else 0
            val vsText    = if (vsLastPct >= 0) "+$vsLastPct%" else "$vsLastPct%"

            val totalCatBudgets = budgets.sumOf { it.amount }

            // ── Insights ─────────────────────────────────────────────────────
            val insights = mutableListOf<Pair<String, String>>()

            if (total == 0.0) {
                insights.add("💡" to "No expenses recorded for this period yet. Start logging to see insights.")
            } else {
                // 1. Min/Max goal zone assessment
                if (maxGoal > 0 && minGoal > 0) {
                    when {
                        total > maxGoal -> {
                            val over = total - maxGoal
                            insights.add("🚨" to "You've exceeded your maximum spending goal " +
                                "(R${"%,.0f".format(maxGoal)}) by R${"%,.0f".format(over)}. " +
                                "Immediate action needed!")
                        }
                        total < minGoal -> {
                            val under = minGoal - total
                            insights.add("💛" to "Spending is R${"%,.0f".format(under)} below your " +
                                "minimum goal (R${"%,.0f".format(minGoal)}). " +
                                "You may have unrecorded expenses.")
                        }
                        else -> {
                            val usedPct = ((total / maxGoal) * 100).toInt()
                            insights.add("✅" to "Spending is within your goal range " +
                                "(R${"%,.0f".format(minGoal)} – R${"%,.0f".format(maxGoal)}). " +
                                "You've used $usedPct% of your max budget. Well done!")
                        }
                    }
                } else if (maxGoal > 0) {
                    when {
                        total > maxGoal -> {
                            val over = total - maxGoal
                            insights.add("🚨" to "You have exceeded your overall monthly budget " +
                                "(R${"%,.0f".format(maxGoal)}) by R${"%,.0f".format(over)}. " +
                                "Review your spending immediately.")
                        }
                        total >= maxGoal * 0.90 -> {
                            val remaining = maxGoal - total
                            insights.add("⚠️" to "You have used ${((total/maxGoal)*100).toInt()}% of your monthly budget. " +
                                "Only R${"%,.0f".format(remaining)} remaining — spend carefully.")
                        }
                        else -> {
                            val remaining = maxGoal - total
                            val usedPct   = ((total / maxGoal) * 100).toInt()
                            insights.add("✅" to "You have used $usedPct% of your monthly budget. " +
                                "R${"%,.0f".format(remaining)} still available.")
                        }
                    }
                }

                // 2. Category budgets sum vs overall budget
                if (maxGoal > 0 && totalCatBudgets > maxGoal) {
                    val excess = totalCatBudgets - maxGoal
                    insights.add("⚠️" to "Your category budgets total R${"%,.0f".format(totalCatBudgets)}, " +
                        "exceeding your max goal by R${"%,.0f".format(excess)}. " +
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
                findViewById<SpendingBarChartView>(R.id.bar_chart).setData(
                    finalBarData,
                    this@StatisticsActivity,
                    showGoals = (maxGoal > 0 || minGoal > 0)
                )
            }
        }
    }
}
