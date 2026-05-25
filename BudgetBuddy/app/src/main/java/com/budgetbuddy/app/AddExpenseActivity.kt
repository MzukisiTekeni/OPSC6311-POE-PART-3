package com.budgetbuddy.app

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.ExpenseCategoryEntity
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseActivity : BaseThemedActivity() {

    private val TAG = "AddExpenseActivity"

    override fun themedBackgroundViewIds() = listOf(R.id.btn_save_expense, R.id.btn_add_category)
    override fun themedSolidViewIds()      = listOf(R.id.v_header_divider)

    private lateinit var repo: BudgetRepository

    // userId is loaded once from SessionManager - never changes mid-session
    private var userId = -1

    // Holds the current category list from the DB for populating the dropdown
    private var categories  = listOf<ExpenseCategoryEntity>()
    private var selectedCat : ExpenseCategoryEntity? = null
    private var receiptUri  : Uri? = null
    private var selectedDate = ""

    // Launcher for the system image picker - fires when the user selects a receipt photo
    private val pickReceipt = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            receiptUri = it
            Log.d(TAG, "Receipt image picked: $it")

            val ivPreview = findViewById<ImageView>(R.id.iv_receipt_preview)
            ivPreview.setImageURI(it)
            ivPreview.visibility = View.VISIBLE
            // Hide the upload placeholder once a receipt is attached
            findViewById<LinearLayout>(R.id.ll_receipt_upload).visibility = View.GONE
            Toast.makeText(this, "Receipt attached ✓", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)
        applyCurrentTheme()

        repo   = BudgetRepository(this)
        userId = SessionManager.getUserId(this)

        Log.d(TAG, "onCreate: AddExpenseActivity started for userId=$userId")

        findViewById<TextView>(R.id.tv_bar_title).text = "Add Expense"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            Log.d(TAG, "Back pressed - discarding unsaved expense")
            finish()
        }

        // ── Categories ────────────────────────────────────────────────────────
        // Live data keeps this list fresh - if the user adds a new category it shows up
        repo.getActiveCategories(userId).observe(this) { cats ->
            categories = cats
            Log.d(TAG, "Categories loaded: ${cats.size} active categories for userId=$userId")

            val noCategories = cats.isEmpty()
            // Show a prompt to add categories if the user hasn't set any up yet
            findViewById<TextView>(R.id.tv_no_categories).visibility =
                if (noCategories) View.VISIBLE else View.GONE
            findViewById<Button>(R.id.btn_add_category).visibility =
                if (noCategories) View.VISIBLE else View.GONE
        }

        // ── Category dropdown ─────────────────────────────────────────────────
        val tvCat    = findViewById<TextView>(R.id.tv_selected_category)
        val catClick = View.OnClickListener { v ->
            if (categories.isEmpty()) {
                Log.w(TAG, "Dropdown opened but no categories exist for userId=$userId")
                Toast.makeText(this, "Add categories first", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }

            Log.d(TAG, "Category dropdown opened - showing ${categories.size} options")
            val popup = PopupMenu(this, v)
            categories.forEachIndexed { i, cat ->
                popup.menu.add(0, i, i, "${cat.emoji} ${cat.name}")
            }

            popup.setOnMenuItemClickListener { item ->
                val chosen = categories[item.itemId]
                Log.d(TAG, "Category selected: '${chosen.name}' (id=${chosen.id})")

                // Before confirming the selection, check a budget exists for this category
                lifecycleScope.launch {
                    val budget = repo.budgetDao.getBudgetForCategory(
                        userId, repo.currentMonth(), chosen.id
                    )
                    runOnUiThread {
                        if (budget == null || budget.amount <= 0) {
                            // Warn the user rather than silently allowing an unbudgeted expense
                            Log.w(TAG, "No budget found for category '${chosen.name}' in ${repo.currentMonth()}")
                            AlertDialog.Builder(this@AddExpenseActivity)
                                .setTitle("⚠️ No Budget Set")
                                .setMessage(
                                    "You haven't set a budget for \"${chosen.emoji} ${chosen.name}\" yet.\n\n" +
                                    "Please set a category budget on the Monthly Budget page before logging expenses here."
                                )
                                .setPositiveButton("Set Budget") { _, _ ->
                                    Log.d(TAG, "User chose to set budget - launching MonthlyBudgetActivity")
                                    startActivity(Intent(this@AddExpenseActivity, MonthlyBudgetActivity::class.java))
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            Log.d(TAG, "Budget confirmed for '${chosen.name}': R${budget.amount}")
                            selectedCat = chosen
                            tvCat.text = "${chosen.emoji} ${chosen.name}"
                            tvCat.setTextColor(getColor(R.color.text_primary))
                        }
                    }
                }
                true
            }
            popup.show()
        }

        tvCat.setOnClickListener(catClick)
        // The dropdown arrow icon and the text view share the same click handler
        findViewById<ImageView>(R.id.iv_dropdown).setOnClickListener(catClick)

        // ── Date picker ───────────────────────────────────────────────────────
        val tvDate = findViewById<TextView>(R.id.tv_date)
        val dateCl = View.OnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(y, m, d)
                // Store the date in yyyy-MM-dd for DB queries, display it as dd/MM/yyyy for readability
                selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                tvDate.text  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
                tvDate.setTextColor(getColor(R.color.text_primary))
                Log.d(TAG, "Date selected: $selectedDate")
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        tvDate.setOnClickListener(dateCl)
        // The calendar icon also opens the date picker
        findViewById<ImageView>(R.id.iv_calendar).setOnClickListener(dateCl)

        // ── Receipt attachment ─────────────────────────────────────────────────
        // Tapping either the placeholder or the preview re-opens the picker
        findViewById<LinearLayout>(R.id.ll_receipt_upload).setOnClickListener {
            Log.d(TAG, "Receipt upload area tapped - opening image picker")
            pickReceipt.launch("image/*")
        }
        findViewById<ImageView>(R.id.iv_receipt_preview).setOnClickListener {
            Log.d(TAG, "Receipt preview tapped - opening image picker to replace receipt")
            pickReceipt.launch("image/*")
        }

        // ── Action buttons ────────────────────────────────────────────────────
        findViewById<Button>(R.id.btn_save_expense).setOnClickListener {
            Log.d(TAG, "Save Expense button tapped")
            saveExpense()
        }
        findViewById<Button>(R.id.btn_add_category).setOnClickListener {
            Log.d(TAG, "Add Category button tapped - launching ExpenseCategoriesActivity")
            startActivity(Intent(this, ExpenseCategoriesActivity::class.java))
        }
    }

    private fun saveExpense() {
        // Strip any currency symbols or formatting the user may have typed
        val amountStr = findViewById<EditText>(R.id.et_amount).text.toString()
            .replace("R", "").replace(" ", "").replace(",", "").trim()
        val amount = amountStr.toDoubleOrNull()
        val desc   = findViewById<EditText>(R.id.et_description).text.toString().trim()

        Log.d(TAG, "saveExpense called - raw amount='$amountStr', parsed=$amount, desc='$desc'")

        // Validate all fields before we try to write anything to the DB
        if (amount == null || amount <= 0) {
            Log.w(TAG, "Validation failed - invalid amount: '$amountStr'")
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedCat == null) {
            Log.w(TAG, "Validation failed - no category selected")
            Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDate.isEmpty()) {
            Log.w(TAG, "Validation failed - no date selected")
            Toast.makeText(this, "Select a date", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Saving expense: R$amount for category='${selectedCat!!.name}' on $selectedDate")

        lifecycleScope.launch {
            // Double-check the budget guard right before saving (category could have been changed)
            val budget = repo.budgetDao.getBudgetForCategory(
                userId, repo.currentMonth(), selectedCat!!.id
            )
            if (budget == null || budget.amount <= 0) {
                Log.w(TAG, "Save blocked - no budget set for '${selectedCat!!.name}'")
                runOnUiThread {
                    AlertDialog.Builder(this@AddExpenseActivity)
                        .setTitle("⚠️ No Budget Set")
                        .setMessage(
                            "You need to set a budget for \"${selectedCat!!.emoji} ${selectedCat!!.name}\" " +
                            "before logging an expense."
                        )
                        .setPositiveButton("Set Budget") { _, _ ->
                            startActivity(Intent(this@AddExpenseActivity, MonthlyBudgetActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                return@launch
            }

            // All good - write the expense to Room
            repo.saveExpense(
                userId      = userId,
                amount      = amount,
                description = desc,
                category    = selectedCat!!,
                date        = selectedDate,
                receiptPath = receiptUri?.toString() ?: ""
            )
            Log.i(TAG, "Expense saved successfully: R$amount, category='${selectedCat!!.name}', date=$selectedDate")

            // Award XP and update the streak for staying active
            if (userId != -1) {
                repo.addXp(userId, 10)
                repo.updateStreak(userId)
                Log.d(TAG, "Awarded 10 XP and updated streak for userId=$userId")

                // Push a notification so the user sees their action was recorded
                repo.addNotification(
                    userId     = userId,
                    icon       = "✅",
                    title      = "Expense logged!",
                    body       = "${selectedCat!!.emoji} $desc — R${"%,.2f".format(amount)}",
                    time       = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
                    tag        = "NUDGE",
                    groupLabel = "Today"
                )
                Log.d(TAG, "Notification added for expense: '$desc'")
            }

            runOnUiThread {
                Toast.makeText(this@AddExpenseActivity, "Saved! +10 XP 🎉", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: AddExpenseActivity destroyed")
    }
}
