package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.ExpenseCategoryEntity
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch

data class CategoryItem(val emoji: String, val name: String, var isSelected: Boolean = false)

class CategoryAdapter(private val items: MutableList<CategoryItem>) :
    RecyclerView.Adapter<CategoryAdapter.VH>() {

    /* ═══════════════════════════════════════════════════════════════
       SECTION 1 — CATEGORY LIST ADAPTER
       ═══════════════════════════════════════════════════════════════ */
    // Manages selection of budget categories from a predefined list.
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmoji: TextView  = view.findViewById(R.id.tv_category_emoji)
        val tvName: TextView   = view.findViewById(R.id.tv_category_name)
        val ivCheck: ImageView = view.findViewById(R.id.iv_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvEmoji.text = item.emoji
        holder.tvName.text  = item.name
        holder.ivCheck.setImageResource(
            if (item.isSelected) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
        )
        holder.itemView.setOnClickListener {
            item.isSelected = !item.isSelected
            notifyItemChanged(position)
        }
    }

    fun selectedItems(): List<CategoryItem> = items.filter { it.isSelected }
}

class ExpenseCategoriesActivity : BaseThemedActivity() {

    /* ═══════════════════════════════════════════════════════════════
       SECTION 2 — CATEGORY SETUP
       ═══════════════════════════════════════════════════════════════ */
    // Initialises the user's category list with defaults or custom additions.
    override fun themedBackgroundViewIds() = listOf(R.id.btn_add_custom, R.id.btn_continue)


    private lateinit var repo: BudgetRepository
    private lateinit var adapter: CategoryAdapter

    private val defaults = mutableListOf(
        CategoryItem("🏠", "Bills & Utilities"),
        CategoryItem("🛒", "Groceries"),
        CategoryItem("🚗", "Transport"),
        CategoryItem("🏥", "Health & Medical"),
        CategoryItem("🍔", "Food & Drinks"),
        CategoryItem("🎬", "Entertainment"),
        CategoryItem("🛍️", "Shopping"),
        CategoryItem("💳", "Debt"),
        CategoryItem("🛡️", "Insurance")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expense_categories)
        applyCurrentTheme()
        repo = BudgetRepository(this)

        findViewById<TextView>(R.id.tv_bar_title).text = getString(R.string.title_expense_categories)
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        adapter = CategoryAdapter(defaults)
        findViewById<RecyclerView>(R.id.rv_categories).apply {
            layoutManager = LinearLayoutManager(this@ExpenseCategoriesActivity)
            adapter        = this@ExpenseCategoriesActivity.adapter
        }

        // Custom category add
        val etCustom = findViewById<EditText>(R.id.et_custom_category)
        findViewById<Button>(R.id.btn_add_custom).setOnClickListener {
            val name = etCustom.text.toString().trim()
            if (name.isNotEmpty()) {
                defaults.add(CategoryItem("💰", name, isSelected = true))
                adapter.notifyItemInserted(defaults.size - 1)
                etCustom.text.clear()
            }
        }

        // Continue — persist to DB
        findViewById<Button>(R.id.btn_continue).setOnClickListener {
            val selected = adapter.selectedItems()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one category", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val userId = SessionManager.getUserId(this@ExpenseCategoriesActivity)
                // Only insert if DB is empty (avoid duplicates on re-run)
                if (repo.categoryDao.countActive(userId) == 0) {
                    repo.saveSelectedCategories(userId, selected.map { it.emoji to it.name })
                } else {
                    selected.forEach { item ->
                        repo.addCustomCategory(userId, item.emoji, item.name)
                    }
                }
                runOnUiThread {
                    val intent = Intent(this@ExpenseCategoriesActivity, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
        }
    }
}
