package com.budgetbuddy.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LogSavingsGoalActivity : BaseThemedActivity() {

    override fun themedBackgroundViewIds() = listOf(R.id.btn_create_goal)


    private lateinit var repo: BudgetRepository
    private var selectedFrequency = "Monthly"
    private var selectedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_savings_goal)
        applyCurrentTheme()
        repo = BudgetRepository(this)

        findViewById<TextView>(R.id.tv_bar_title).text = "Log a Savings Goal"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        // Date picker
        val tvDate = findViewById<TextView>(R.id.tv_target_date)
        val dateCl = android.view.View.OnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                tvDate.text  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                tvDate.setTextColor(getColor(R.color.text_primary))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        tvDate.setOnClickListener(dateCl)
        findViewById<ImageView>(R.id.iv_cal).setOnClickListener(dateCl)

        // Frequency chips
        fun selectChip(chosen: String) {
            selectedFrequency = chosen
            mapOf(
                "Weekly"  to R.id.chip_weekly,
                "Monthly" to R.id.chip_monthly,
                "Custom"  to R.id.chip_custom
            ).forEach { (label, id) ->
                val chip = findViewById<TextView>(id)
                if (label == chosen) {
                    val primary = ThemeManager.getPalette(this).primary
                    ThemeManager.tintBackground(chip, primary)
                    chip.setTextColor(getColor(R.color.text_on_primary))
                } else {
                    chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                    chip.setTextColor(getColor(R.color.text_secondary))
                }
            }
        }
        selectChip("Monthly")
        findViewById<TextView>(R.id.chip_weekly).setOnClickListener  { selectChip("Weekly") }
        findViewById<TextView>(R.id.chip_monthly).setOnClickListener { selectChip("Monthly") }
        findViewById<TextView>(R.id.chip_custom).setOnClickListener  { selectChip("Custom") }

        // Create goal
        findViewById<android.widget.Button>(R.id.btn_create_goal).setOnClickListener {
            val amountStr = findViewById<EditText>(R.id.et_goal_amount).text.toString()
                .replace("R", "").replace(" ", "").trim()
            val amount = amountStr.toDoubleOrNull()
            val name   = findViewById<EditText>(R.id.et_goal_name).text.toString().trim()

            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter target amount", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a goal name", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Choose a target date", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            lifecycleScope.launch {
                val userId = SessionManager.getUserId(this@LogSavingsGoalActivity)
                repo.createSavingsGoal(userId, name, amount, selectedDate, selectedFrequency, 0.0)
                runOnUiThread {
                    Toast.makeText(this@LogSavingsGoalActivity,
                        "Goal created! +200 XP on completion 🎯", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
