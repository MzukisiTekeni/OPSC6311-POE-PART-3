package com.budgetbuddy.app

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.widget.*
// AppCompatActivity replaced by BaseThemedActivity
import androidx.lifecycle.lifecycleScope
import com.budgetbuddy.app.db.BudgetRepository
import com.budgetbuddy.app.db.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : BaseThemedActivity() {

    // Tag for logcat - I'm using the class name so I can filter logs easily
    private val TAG = "LoginActivity"

    private lateinit var repo: BudgetRepository

    // Tracks whether the password is currently visible - starts hidden by default
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        repo = BudgetRepository(this)

        Log.d(TAG, "onCreate: Login screen is up and ready")

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)

        // Back arrow - just closes this activity and goes back
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            Log.d(TAG, "Back button clicked - finishing activity")
            finish()
        }

        // Toggle the password between dots and plain text
        findViewById<ImageView>(R.id.iv_toggle_password).setOnClickListener {
            passwordVisible = !passwordVisible
            Log.d(TAG, "Password visibility toggled - now visible: $passwordVisible")
            etPassword.transformationMethod =
                if (passwordVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            // Keep the cursor at the end so it doesn't jump around
            etPassword.setSelection(etPassword.text.length)
        }

        // Navigate to forgot password screen
        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            Log.d(TAG, "User tapped Forgot Password - launching ForgotPasswordActivity")
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Main login button - validates fields then checks credentials in the DB
        findViewById<Button>(R.id.btn_log_in).setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            // Basic validation before we even hit the database
            if (username.isEmpty()) {
                Log.w(TAG, "Login attempt blocked - username field is empty")
                toast("Enter your username")
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Log.w(TAG, "Login attempt blocked - password field is empty")
                toast("Enter your password")
                return@setOnClickListener
            }

            Log.d(TAG, "Login attempt started for username: $username")

            // Run the DB lookup on a coroutine so we don't freeze the UI thread
            lifecycleScope.launch {
                val user = repo.loginUser(username, password)
                runOnUiThread {
                    if (user == null) {
                        // Either username doesn't exist or password was wrong
                        Log.w(TAG, "Login failed - no matching user found for username: $username")
                        toast("Invalid username or password")
                    } else {
                        // Save the userId to SharedPreferences so every screen knows who's logged in
                        Log.i(TAG, "Login successful for userId=${user.id}, username=${user.username}")
                        SessionManager.saveUserId(this@LoginActivity, user.id)

                        val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                        // Clear the back stack so the user can't press back to return to login
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }
            }
        }

        // If the user doesn't have an account yet, send them to register
        findViewById<TextView>(R.id.tv_go_register).setOnClickListener {
            Log.d(TAG, "User wants to register - navigating to RegisterActivity")
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: LoginActivity is being destroyed")
    }

    // Small helper so I'm not repeating Toast.makeText everywhere
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
