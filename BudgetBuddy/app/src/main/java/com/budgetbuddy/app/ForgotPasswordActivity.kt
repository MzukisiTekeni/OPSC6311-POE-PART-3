package com.budgetbuddy.app

import android.os.Bundle
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BudgetRepository
import kotlinx.coroutines.launch

class ForgotPasswordActivity : BaseThemedActivity() {
    /* ═══════════════════════════════════════════════════════════════
       SECTION 1 — RECOVERY FLOW
       ═══════════════════════════════════════════════════════════════ */
    // Handles email-based account recovery and verification.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val repo = BudgetRepository(this)
        findViewById<TextView>(R.id.tv_bar_title).text = "Forgot your password?"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_send_password).setOnClickListener {
            val email = findViewById<EditText>(R.id.et_reset_email).text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val user = repo.userDao.getUserByEmail(email)
                runOnUiThread {
                    if (user == null) {
                        Toast.makeText(this@ForgotPasswordActivity,
                            "No account found with that email", Toast.LENGTH_SHORT).show()
                    } else {
                        // In a real app: send reset email. Here we just confirm.
                        Toast.makeText(this@ForgotPasswordActivity,
                            "Account found. Contact support to reset your password.",
                            Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}
