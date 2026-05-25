package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch

class RegisterActivity : BaseThemedActivity() {

    private val TAG = "RegisterActivity"

    private lateinit var repo: BudgetRepository

    // Separate visibility flags for both password fields
    private var passwordVisible = false
    private var confirmVisible  = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        repo = BudgetRepository(this)

        Log.d(TAG, "onCreate: Registration screen loaded")

        findViewById<TextView>(R.id.tv_bar_title).text = "Registration"
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            Log.d(TAG, "Back arrow tapped - closing registration")
            finish()
        }

        val etPassword = findViewById<android.widget.EditText>(R.id.et_password)
        val etConfirm  = findViewById<android.widget.EditText>(R.id.et_confirm_password)

        // Toggle for the main password field
        findViewById<ImageView>(R.id.iv_toggle_password).setOnClickListener {
            passwordVisible = !passwordVisible
            Log.d(TAG, "Password field visibility toggled: $passwordVisible")
            etPassword.transformationMethod =
                if (passwordVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            etPassword.setSelection(etPassword.text.length)
        }

        // Toggle for the confirm password field
        findViewById<ImageView>(R.id.iv_toggle_confirm).setOnClickListener {
            confirmVisible = !confirmVisible
            Log.d(TAG, "Confirm password visibility toggled: $confirmVisible")
            etConfirm.transformationMethod =
                if (confirmVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            etConfirm.setSelection(etConfirm.text.length)
        }

        // Register button - validate everything before touching the database
        findViewById<Button>(R.id.btn_register).setOnClickListener {
            val username = findViewById<android.widget.EditText>(R.id.et_username).text.toString().trim()
            val email    = findViewById<android.widget.EditText>(R.id.et_email).text.toString().trim()
            val password = etPassword.text.toString()
            val confirm  = etConfirm.text.toString()

            Log.d(TAG, "Register button clicked for username: $username, email: $email")

            // Run through each validation rule one at a time
            if (username.isEmpty()) {
                Log.w(TAG, "Validation failed - username is empty")
                showError("Username is required")
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Log.w(TAG, "Validation failed - invalid email format: $email")
                showError("Enter a valid email")
                return@setOnClickListener
            }
            if (password.length < 6) {
                Log.w(TAG, "Validation failed - password too short (${password.length} chars)")
                showError("Password must be 6+ characters")
                return@setOnClickListener
            }
            if (password != confirm) {
                Log.w(TAG, "Validation failed - passwords do not match")
                showError("Passwords do not match")
                return@setOnClickListener
            }

            Log.d(TAG, "All fields passed validation - proceeding to DB check")

            lifecycleScope.launch {
                // Make sure the username isn't already taken before inserting
                if (repo.userDao.getUserByUsername(username) != null) {
                    Log.w(TAG, "Registration blocked - username '$username' is already taken")
                    runOnUiThread { showError("Username already taken") }
                    return@launch
                }

                Log.d(TAG, "Username is available - creating new account for: $username")
                val userId = repo.registerUser(username, email, password)
                Log.i(TAG, "New user registered successfully with userId=$userId")

                // Save the session so they're considered logged in right away
                SessionManager.saveUserId(this@RegisterActivity, userId.toInt())

                runOnUiThread {
                    // Send them to login rather than dashboard so they confirm their creds
                    Log.d(TAG, "Navigating to LoginActivity after successful registration")
                    startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }

        // Already have an account link at the bottom
        findViewById<TextView>(R.id.tv_go_login).setOnClickListener {
            Log.d(TAG, "User already has an account - navigating to LoginActivity")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: RegisterActivity cleaned up")
    }

    private fun showError(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
