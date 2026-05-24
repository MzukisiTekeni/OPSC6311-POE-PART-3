package com.budgetbuddy.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BaseThemedActivity
 * ─────────────────────────────────────────────────────────────────
 * All activities extend this.  On every onResume() the current
 * theme accent colour is re-applied so navigating back from
 * ProfileActivity picks up a newly chosen theme immediately.
 *
 * Dark mode is handled entirely by the values-night/colors.xml
 * resource qualifier (switched via AppCompatDelegate in
 * DarkModeManager).  The accent colours from ThemeManager are
 * applied on top of whatever surface colours the night qualifier
 * provides, so every XP theme works perfectly in both light and
 * dark mode.
 *
 * Navigation icons are NEVER tinted by the theme — their original
 * multi-colour SVG artwork is preserved by setting itemIconTintList
 * to null on the BottomNavigationView.
 */
abstract class BaseThemedActivity : AppCompatActivity() {

    /** Views whose background GradientDrawable should be filled with primary */
    open fun themedBackgroundViewIds(): List<Int> = emptyList()

    /** CardViews whose card background should be set to primary */
    open fun themedCardViewIds(): List<Int> = emptyList()

    /** Plain Views (dividers, solid blocks) whose background colour = primary */
    open fun themedSolidViewIds(): List<Int> = emptyList()

    /** ProgressBars whose progress tint = primary */
    open fun themedProgressBarIds(): List<Int> = emptyList()

    /** TextViews whose text colour = primary */
    open fun themedTextViewIds(): List<Int> = emptyList()

    /** ImageViews whose imageTint = primary (use for non-SVG icons only) */
    open fun themedImageViewIds(): List<Int> = emptyList()

    /**
     * ImageViews that use the theme accent as their tint.
     * Same as themedImageViewIds() but semantically explicit —
     * both lists are tinted with primary; subclasses may use either.
     */
    open fun themedIconViewIds(): List<Int> = emptyList()

    /** BottomNavigationView ID (0 = absent) */
    open fun bottomNavId(): Int = 0

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        applyCurrentTheme()
    }

    // ── Theme application ─────────────────────────────────────────────────────

    protected fun applyCurrentTheme() {
        val palette = ThemeManager.getPalette(this)
        val primary = palette.primary

        // Rounded-background views: buttons, avatar circles, chips
        themedBackgroundViewIds().forEach { id ->
            runCatching { ThemeManager.tintBackground(findViewById(id), primary) }
        }

        // CardViews with solid primary background
        themedCardViewIds().forEach { id ->
            runCatching { findViewById<CardView>(id).setCardBackgroundColor(primary) }
        }

        // Plain View dividers / solid colour blocks
        themedSolidViewIds().forEach { id ->
            runCatching { findViewById<View>(id).setBackgroundColor(primary) }
        }

        // Progress bars
        themedProgressBarIds().forEach { id ->
            runCatching { ThemeManager.tintProgressBar(findViewById(id), primary) }
        }

        // Text views
        themedTextViewIds().forEach { id ->
            runCatching { ThemeManager.tintText(findViewById(id), primary) }
        }

        // Image views tinted with accent (non-SVG icons, arrows, etc.)
        (themedImageViewIds() + themedIconViewIds()).forEach { id ->
            runCatching { ThemeManager.tintImage(findViewById(id), primary) }
        }

        // ── Bottom Navigation ─────────────────────────────────────────────────
        // Original multi-colour SVG icons are preserved by setting
        // itemIconTintList = null.  Only the label text uses the theme colour.
        val navId = bottomNavId()
        if (navId != 0) {
            runCatching {
                val nav = findViewById<BottomNavigationView>(navId)

                // Preserve original SVG artwork — no icon tinting at all.
                nav.itemIconTintList = null

                // Labels: selected = theme primary, unselected = muted grey.
                val unselectedColor = resolveColor(android.R.attr.textColorSecondary,
                    0xFF9CA3AF.toInt())
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                nav.itemTextColor = ColorStateList(states, intArrayOf(primary, unselectedColor))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns an AlertDialog.Builder pre-styled with the app's surface colour
     * so dialogs look correct in both light and dark mode.
     */
    fun themedDialogBuilder() = androidx.appcompat.app.AlertDialog.Builder(
        this, R.style.Style_ThemedDialog
    )

    private fun resolveColor(attr: Int, fallback: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, fallback)
        ta.recycle()
        return color
    }
}
