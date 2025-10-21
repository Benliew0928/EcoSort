package com.example.ecosort.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

/**
 * Responsive utility class for Bootstrap-like responsive design in Android
 * Provides methods to adapt UI elements based on screen size and orientation
 */
object ResponsiveUtils {

    /**
     * Screen size categories similar to Bootstrap breakpoints
     */
    enum class ScreenSize {
        XS,    // Extra small phones (320dp and below)
        SM,    // Small phones (360dp)
        MD,    // Medium phones (400dp and above)
        LG     // Large phones (480dp and above)
    }

    /**
     * Get current screen size category
     */
    fun getScreenSize(context: Context): ScreenSize {
        val displayMetrics = context.resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        
        return when {
            screenWidthDp <= 320 -> ScreenSize.XS
            screenWidthDp <= 360 -> ScreenSize.SM
            screenWidthDp <= 400 -> ScreenSize.MD
            else -> ScreenSize.LG
        }
    }

    /**
     * Check if device is in landscape orientation
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Get responsive padding based on screen size
     */
    fun getResponsivePadding(context: Context, basePadding: Int): Int {
        val screenSize = getScreenSize(context)
        val multiplier = when (screenSize) {
            ScreenSize.XS -> 0.75f
            ScreenSize.SM -> 1.0f
            ScreenSize.MD -> 1.25f
            ScreenSize.LG -> 1.5f
        }
        return (basePadding * multiplier).toInt()
    }

    /**
     * Get responsive text size based on screen size
     */
    fun getResponsiveTextSize(context: Context, baseTextSize: Float): Float {
        val screenSize = getScreenSize(context)
        val multiplier = when (screenSize) {
            ScreenSize.XS -> 0.9f
            ScreenSize.SM -> 1.0f
            ScreenSize.MD -> 1.1f
            ScreenSize.LG -> 1.2f
        }
        return baseTextSize * multiplier
    }

    /**
     * Apply responsive margins to a view
     */
    fun applyResponsiveMargins(view: View, context: Context) {
        val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams
        if (layoutParams != null) {
            val screenSize = getScreenSize(context)
            val marginMultiplier = when (screenSize) {
                ScreenSize.XS -> 0.75f
                ScreenSize.SM -> 1.0f
                ScreenSize.MD -> 1.25f
                ScreenSize.LG -> 1.5f
            }
            
            layoutParams.leftMargin = (layoutParams.leftMargin * marginMultiplier).toInt()
            layoutParams.rightMargin = (layoutParams.rightMargin * marginMultiplier).toInt()
            layoutParams.topMargin = (layoutParams.topMargin * marginMultiplier).toInt()
            layoutParams.bottomMargin = (layoutParams.bottomMargin * marginMultiplier).toInt()
            
            view.layoutParams = layoutParams
        }
    }

    /**
     * Apply responsive padding to a view
     */
    fun applyResponsivePadding(view: View, context: Context) {
        val screenSize = getScreenSize(context)
        val paddingMultiplier = when (screenSize) {
            ScreenSize.XS -> 0.75f
            ScreenSize.SM -> 1.0f
            ScreenSize.MD -> 1.25f
            ScreenSize.LG -> 1.5f
        }
        
        val left = (view.paddingLeft * paddingMultiplier).toInt()
        val right = (view.paddingRight * paddingMultiplier).toInt()
        val top = (view.paddingTop * paddingMultiplier).toInt()
        val bottom = (view.paddingBottom * paddingMultiplier).toInt()
        
        view.setPadding(left, top, right, bottom)
    }

    /**
     * Configure responsive grid layout
     * Similar to Bootstrap's grid system
     */
    fun configureResponsiveGrid(
        container: LinearLayout,
        context: Context,
        itemCount: Int,
        itemView: (Int) -> View
    ) {
        val screenSize = getScreenSize(context)
        val isLandscape = isLandscape(context)
        
        // Determine columns based on screen size and orientation
        val columns = when {
            isLandscape -> when (screenSize) {
                ScreenSize.XS -> 2
                ScreenSize.SM -> 3
                ScreenSize.MD -> 4
                ScreenSize.LG -> 5
            }
            else -> when (screenSize) {
                ScreenSize.XS -> 1
                ScreenSize.SM -> 2
                ScreenSize.MD -> 2
                ScreenSize.LG -> 3
            }
        }
        
        // Clear existing views
        container.removeAllViews()
        
        // Create rows and add items
        var currentRow: LinearLayout? = null
        for (i in 0 until itemCount) {
            if (i % columns == 0) {
                // Create new row
                currentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                container.addView(currentRow)
            }
            
            // Add item to current row
            val item = itemView(i)
            val itemParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = if (i % columns < columns - 1) dpToPx(context, 8) else 0
            }
            
            currentRow?.addView(item, itemParams)
        }
    }

    /**
     * Apply responsive constraints to ConstraintLayout
     */
    fun applyResponsiveConstraints(
        constraintLayout: ConstraintLayout,
        context: Context,
        viewId: Int,
        constraints: (ConstraintSet, Int) -> Unit
    ) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraints(constraintSet, viewId)
        constraintSet.applyTo(constraintLayout)
    }

    /**
     * Get responsive button height
     */
    fun getResponsiveButtonHeight(context: Context, baseHeight: Int): Int {
        val screenSize = getScreenSize(context)
        val multiplier = when (screenSize) {
            ScreenSize.XS -> 0.9f
            ScreenSize.SM -> 1.0f
            ScreenSize.MD -> 1.1f
            ScreenSize.LG -> 1.2f
        }
        return (baseHeight * multiplier).toInt()
    }

    /**
     * Get responsive icon size
     */
    fun getResponsiveIconSize(context: Context, baseSize: Int): Int {
        val screenSize = getScreenSize(context)
        val multiplier = when (screenSize) {
            ScreenSize.XS -> 0.8f
            ScreenSize.SM -> 1.0f
            ScreenSize.MD -> 1.1f
            ScreenSize.LG -> 1.2f
        }
        return (baseSize * multiplier).toInt()
    }

    /**
     * Convert dp to pixels
     */
    fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Convert sp to pixels
     */
    fun spToPx(context: Context, sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }

    /**
     * Get responsive card width for horizontal scrolling
     */
    fun getResponsiveCardWidth(context: Context): Int {
        val screenSize = getScreenSize(context)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        
        return when (screenSize) {
            ScreenSize.XS -> (screenWidth * 0.8f / density).toInt()
            ScreenSize.SM -> (screenWidth * 0.7f / density).toInt()
            ScreenSize.MD -> (screenWidth * 0.6f / density).toInt()
            ScreenSize.LG -> (screenWidth * 0.5f / density).toInt()
        }
    }

    /**
     * Apply responsive layout to a view based on screen size
     */
    fun applyResponsiveLayout(view: View, context: Context) {
        val screenSize = getScreenSize(context)
        val isLandscape = isLandscape(context)
        
        when (screenSize) {
            ScreenSize.XS -> {
                // Compact layout for small screens
                applyResponsivePadding(view, context)
                applyResponsiveMargins(view, context)
            }
            ScreenSize.SM -> {
                // Standard layout
                // No special adjustments needed
            }
            ScreenSize.MD -> {
                // Slightly larger layout
                applyResponsivePadding(view, context)
                applyResponsiveMargins(view, context)
            }
            ScreenSize.LG -> {
                // Generous layout for large screens
                applyResponsivePadding(view, context)
                applyResponsiveMargins(view, context)
            }
        }
    }
}
