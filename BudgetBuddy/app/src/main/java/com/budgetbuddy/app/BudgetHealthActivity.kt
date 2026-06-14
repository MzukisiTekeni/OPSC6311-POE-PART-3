package com.budgetbuddy.app

import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.budgetbuddy.app.db.BudgetRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Adapter ───────────────────────────────────────────────────────────────────
/* ═══════════════════════════════════════════════════════════════
   SECTION 1 — CATEGORY BREAKDOWN ADAPTER
   ═══════════════════════════════════════════════════════════════ */
// Adapter for displaying health metrics per category (spent vs budget)
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
/* ═══════════════════════════════════════════════════════════════
   SECTION 2 — CUSTOM HEALTH GAUGES
   ═══════════════════════════════════════════════════════════════ */
// Custom donut and gauge views for visualising health scores and spending zones
class DonutScoreView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private var score = 0

    private val paintTrack = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style  = android.graphics.Paint.Style.STROKE
        strokeWidth = 0f
    }
    private val paintArc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style        = android.graphics.Paint.Style.STROKE
        strokeCap    = android.graphics.Paint.Cap.ROUND
        strokeWidth  = 0f
    }
    private val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
    }
    private val paintSub = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
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
        canvas.drawArc(oval, -90f, 360f, false, paintTrack)
        val sweepAngle = (score / 100f * 360f)
        val arcColor = when {
            score >= 80 -> Color.parseColor("#22C55E")
            score >= 55 -> Color.parseColor("#F59E0B")
            else        -> Color.parseColor("#EF4444")
        }
        paintArc.color = arcColor
        canvas.drawArc(oval, -90f, sweepAngle, false, paintArc)
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

// ── Goal Zone Gauge View ──────────────────────────────────────────────────────
// Displays a horizontal bar showing where current spending sits relative to
// the minimum and maximum spending goals, with colour zones.
class GoalZoneGaugeView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var spent   = 0.0
    private var minGoal = 0.0
    private var maxGoal = 0.0

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintZoneOk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E"); style = Paint.Style.FILL; alpha = 50
    }
    private val paintZoneLow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B"); style = Paint.Style.FILL; alpha = 50
    }
    private val paintZoneHigh = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444"); style = Paint.Style.FILL; alpha = 50
    }
    private val paintSpentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f; textAlign = Paint.Align.CENTER
    }
    private val paintBoldLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val paintMarker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
        setShadowLayer(6f, 0f, 2f, Color.parseColor("#44000000"))
    }

    init {
        val isDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        paintBg.color    = if (isDark) 0xFF2E3348.toInt() else 0xFFE8EDF3.toInt()
        paintLabel.color = if (isDark) 0xFF9BA3B8.toInt() else 0xFF6B7280.toInt()
        paintBoldLabel.color = if (isDark) 0xFFF0F4F8.toInt() else 0xFF1A1A2E.toInt()
    }

    fun setGoals(spent: Double, minGoal: Double, maxGoal: Double) {
        this.spent   = spent
        this.minGoal = minGoal
        this.maxGoal = maxGoal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (maxGoal <= 0.0) {
            paintLabel.textAlign = Paint.Align.CENTER
            canvas.drawText("Set a budget to see the goal zone", width / 2f, height / 2f, paintLabel)
            return
        }

        val padH  = 48f
        val padV  = 60f
        val barH  = (height - padV * 2) * 0.45f
        val barY  = padV
        val barW  = width - padH * 2f

        // The scale goes from 0 to max(spent, maxGoal)*1.15 so there's always room
        val scale = maxOf(spent, maxGoal) * 1.15

        fun xFor(value: Double) = padH + (value / scale * barW).toFloat()

        val xMin  = if (minGoal > 0) xFor(minGoal) else padH
        val xMax  = xFor(maxGoal)
        val xSpent = xFor(spent).coerceIn(padH, padH + barW)

        // Background track
        canvas.drawRoundRect(padH, barY, padH + barW, barY + barH, barH / 2, barH / 2, paintBg)

        // Zone: 0 → min = amber (below minimum)
        if (minGoal > 0) {
            canvas.drawRoundRect(padH, barY, xMin, barY + barH, barH / 2, barH / 2, paintZoneLow)
        }

        // Zone: min → max = green (healthy range)
        canvas.drawRoundRect(xMin, barY, xMax, barY + barH, 0f, 0f, paintZoneOk)

        // Zone: max → end = red (overspend)
        canvas.drawRoundRect(xMax, barY, padH + barW, barY + barH, barH / 2, barH / 2, paintZoneHigh)

        // Spent fill bar
        val spentColor = when {
            spent > maxGoal             -> Color.parseColor("#EF4444")
            minGoal > 0 && spent < minGoal -> Color.parseColor("#F59E0B")
            else                        -> Color.parseColor("#22C55E")
        }
        paintSpentBar.color = spentColor
        canvas.drawRoundRect(padH, barY, xSpent, barY + barH, barH / 2, barH / 2, paintSpentBar)

        // Min goal line
        if (minGoal > 0) {
            paintLine.color = Color.parseColor("#F59E0B")
            canvas.drawLine(xMin, barY - 8f, xMin, barY + barH + 8f, paintLine)
        }

        // Max goal line
        paintLine.color = Color.parseColor("#EF4444")
        canvas.drawLine(xMax, barY - 8f, xMax, barY + barH + 8f, paintLine)

        // Spent marker (white circle on the bar edge)
        canvas.drawCircle(xSpent, barY + barH / 2, barH * 0.6f, paintMarker)
        val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = spentColor
        }
        canvas.drawCircle(xSpent, barY + barH / 2, barH * 0.6f, markerStroke)

        // Labels below
        val labelY = barY + barH + 40f

        if (minGoal > 0) {
            canvas.drawText("Min\nR${"%,.0f".format(minGoal)}", xMin, labelY, paintLabel)
        }
        canvas.drawText("Max\nR${"%,.0f".format(maxGoal)}", xMax, labelY, paintLabel)

        // Status text
        val statusText = when {
            spent > maxGoal             -> "⚠ Over max by R${"%,.0f".format(spent - maxGoal)}"
            minGoal > 0 && spent < minGoal -> "💛 R${"%,.0f".format(minGoal - spent)} below minimum"
            else                        -> "✓ Within healthy range"
        }
        val statusColor = when {
            spent > maxGoal             -> Color.parseColor("#EF4444")
            minGoal > 0 && spent < minGoal -> Color.parseColor("#F59E0B")
            else                        -> Color.parseColor("#22C55E")
        }
        paintBoldLabel.color = statusColor
        canvas.drawText(statusText, width / 2f, barY + barH + 90f, paintBoldLabel)
    }
}

// ── Activity ──────────────────────────────────────────────────────────────────
/* ═══════════════════════════════════════════════════════════════
   SECTION 3 — BUDGET HEALTH HOME
   ═══════════════════════════════════════════════════════════════ */
// Aggregates spending data to calculate a 0-100 health score
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
                val budgetMap      = budgetList.associateBy { it.categoryName }
                val maxGoal        = repo.loadOverallBudget(this, userId)
                val minGoal        = repo.loadMinGoal(this, userId)
                val totalCatBudgets = budgetList.sumOf { it.amount }
                val totalSpent     = spendingList.sumOf { it.total }

                val rows = spendingList.map { s ->
                    val budget = budgetMap[s.categoryName]?.amount ?: 0.0
                    HealthRowAdapter.HealthRow(s.categoryEmoji, s.categoryName, s.total, budget)
                }

                adapter.update(rows)

                // Score
                val score = computeScore(rows, maxGoal, minGoal, totalSpent)
                findViewById<DonutScoreView>(R.id.donut_health_score).setScore(score)
                val labelRes = when {
                    score >= 80 -> "Great 🟢"
                    score >= 55 -> "Fair 🟡"
                    else        -> "At Risk 🔴"
                }
                findViewById<TextView>(R.id.tv_health_label).text = labelRes

                // Goal Zone Gauge — shows how spending sits between min and max goals
                val gauge = findViewById<GoalZoneGaugeView?>(R.id.goal_zone_gauge)
                gauge?.setGoals(totalSpent, minGoal, maxGoal)

                // Goal zone label above gauge
                val gaugeTitle = findViewById<TextView?>(R.id.tv_goal_zone_title)
                if (maxGoal > 0 && minGoal > 0) {
                    gaugeTitle?.text = "Spending Goal Zone  •  R${"%,.0f".format(minGoal)} – R${"%,.0f".format(maxGoal)}"
                } else if (maxGoal > 0) {
                    gaugeTitle?.text = "Spending vs Max Budget  •  R${"%,.0f".format(maxGoal)}"
                } else {
                    gaugeTitle?.text = "Goal Zone (set a budget to activate)"
                }

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

                // Build summary
                val summaryParts = mutableListOf<String>()

                // 1. Min/Max goal zone status
                if (maxGoal > 0 && minGoal > 0) {
                    when {
                        totalSpent > maxGoal -> {
                            val over = totalSpent - maxGoal
                            summaryParts.add(
                                "🚨 You've exceeded your maximum goal (R${"%,.0f".format(maxGoal)}) " +
                                "by R${"%,.0f".format(over)}. Cut back spending immediately."
                            )
                        }
                        totalSpent < minGoal -> {
                            val under = minGoal - totalSpent
                            summaryParts.add(
                                "💛 Spending (R${"%,.0f".format(totalSpent)}) is R${"%,.0f".format(under)} " +
                                "below your minimum goal. You may have unlogged expenses."
                            )
                        }
                        else -> {
                            val pct = ((totalSpent / maxGoal) * 100).toInt()
                            summaryParts.add(
                                "✅ Spending is within your healthy range " +
                                "(R${"%,.0f".format(minGoal)} – R${"%,.0f".format(maxGoal)}). " +
                                "You've used $pct% of your max budget this month. 🎉"
                            )
                        }
                    }
                } else if (maxGoal > 0) {
                    if (totalCatBudgets > maxGoal) {
                        val excess = totalCatBudgets - maxGoal
                        summaryParts.add(
                            "⚠️ Your category budgets total R${"%,.0f".format(totalCatBudgets)}, " +
                            "exceeding your overall budget of R${"%,.0f".format(maxGoal)} " +
                            "by R${"%,.0f".format(excess)}."
                        )
                    }

                    if (totalSpent > maxGoal) {
                        val over = totalSpent - maxGoal
                        val totalSpentStr = "%,.0f".format(totalSpent)
                        summaryParts.add(
                            "🚨 Total spending (R$totalSpentStr) has exceeded your " +
                            "monthly budget by R${"%,.0f".format(over)}."
                        )
                    } else {
                        val remaining = maxGoal - totalSpent
                        val usedPct   = (totalSpent / maxGoal * 100).toInt()
                        summaryParts.add(
                            "Overall: R${"%,.0f".format(totalSpent)} spent of " +
                            "R${"%,.0f".format(maxGoal)} ($usedPct%). " +
                            "R${"%,.0f".format(remaining)} remaining this month."
                        )
                    }
                }

                // 2. Per-category overspending
                val overRows = rows.filter { it.isOver }
                if (overRows.isNotEmpty()) {
                    val parts = overRows.joinToString(", ") {
                        val overAmount = it.spent - it.budget
                        "${it.category} by R${"%,.0f".format(overAmount)}"
                    }
                    val totalOver = overRows.sumOf { it.spent - it.budget }
                    summaryParts.add(
                        "Over category budget on $parts — " +
                        "R${"%,.0f".format(totalOver)} in unplanned spending."
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
        maxGoal: Double,
        minGoal: Double,
        totalSpent: Double
    ): Int {
        var score = 100

        // Deduct for each category over its own budget
        rows.filter { it.isOver }.forEach { r ->
            val overPct = if (r.budget > 0) ((r.spent - r.budget) / r.budget * 100).toInt() else 50
            score -= (10 + overPct / 10).coerceAtMost(25)
        }

        // Deduct for spending exceeding max goal
        if (maxGoal > 0 && totalSpent > maxGoal) {
            val overPct = ((totalSpent - maxGoal) / maxGoal * 100).toInt()
            score -= (15 + overPct / 5).coerceAtMost(30)
        }

        // Deduct if spending is below the minimum goal (indicates untracked spending)
        if (minGoal > 0 && totalSpent > 0 && totalSpent < minGoal) {
            score -= 10
        }

        return score.coerceAtLeast(0)
    }
}
