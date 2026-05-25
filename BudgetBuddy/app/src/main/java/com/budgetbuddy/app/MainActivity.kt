package com.budgetbuddy.app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
// AppCompatActivity replaced by BaseThemedActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * MainActivity - the first screen the system opens.
 *
 * This is mostly a splash/welcome wrapper. The real navigation logic
 * is handled by the WelcomeActivity and LoginActivity from here.
 * I'm using enableEdgeToEdge() so the layout stretches under the status bar.
 */
class MainActivity : BaseThemedActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate: MainActivity started - setting up edge-to-edge insets")

        // Pad the root view by the system bar heights so content isn't hidden behind them
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            Log.d(TAG, "Window insets applied: top=${systemBars.top}, bottom=${systemBars.bottom}")
            insets
        }
    }
}
