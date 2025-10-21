package com.example.ecosort.utils

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * Responsive Layout Manager for RecyclerView
 * Automatically adjusts layout based on screen size and orientation
 * Similar to Bootstrap's responsive grid system
 */
class ResponsiveLayoutManager private constructor(
    private val context: Context,
    private val baseColumns: Int = 1
) {

    companion object {
        /**
         * Create a responsive layout manager
         */
        fun create(context: Context, baseColumns: Int = 1): ResponsiveLayoutManager {
            return ResponsiveLayoutManager(context, baseColumns)
        }
    }

    /**
     * Get appropriate layout manager based on screen size and orientation
     */
    fun getLayoutManager(): RecyclerView.LayoutManager {
        val screenSize = ResponsiveUtils.getScreenSize(context)
        val isLandscape = ResponsiveUtils.isLandscape(context)
        
        return when {
            baseColumns > 1 -> {
                // Grid layout
                val columns = getResponsiveColumns(screenSize, isLandscape)
                GridLayoutManager(context, columns)
            }
            else -> {
                // Linear layout
                if (isLandscape && screenSize >= ResponsiveUtils.ScreenSize.MD) {
                    // Use staggered grid for landscape on medium+ screens
                    StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                } else {
                    // Use linear layout for portrait or small screens
                    LinearLayoutManager(context)
                }
            }
        }
    }

    /**
     * Get responsive number of columns based on screen size and orientation
     */
    private fun getResponsiveColumns(screenSize: ResponsiveUtils.ScreenSize, isLandscape: Boolean): Int {
        return when {
            isLandscape -> {
                // Landscape orientation - more columns
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns)
                    ResponsiveUtils.ScreenSize.SM -> maxOf(2, baseColumns + 1)
                    ResponsiveUtils.ScreenSize.MD -> maxOf(3, baseColumns + 2)
                    ResponsiveUtils.ScreenSize.LG -> maxOf(4, baseColumns + 3)
                }
            }
            else -> {
                // Portrait orientation - fewer columns
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns - 1)
                    ResponsiveUtils.ScreenSize.SM -> baseColumns
                    ResponsiveUtils.ScreenSize.MD -> baseColumns + 1
                    ResponsiveUtils.ScreenSize.LG -> baseColumns + 2
                }
            }
        }
    }
}

/**
 * Responsive Grid Layout Manager
 * Automatically adjusts grid columns based on screen size
 */
class ResponsiveGridLayoutManager(
    private val context: Context,
    private val baseColumns: Int = 2
) : GridLayoutManager(context, baseColumns) {

    init {
        updateSpanCount(context)
    }

    private fun updateSpanCount(context: Context) {
        val screenSize = ResponsiveUtils.getScreenSize(context)
        val isLandscape = ResponsiveUtils.isLandscape(context)
        
        val columns = when {
            isLandscape -> {
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns)
                    ResponsiveUtils.ScreenSize.SM -> maxOf(2, baseColumns + 1)
                    ResponsiveUtils.ScreenSize.MD -> maxOf(3, baseColumns + 2)
                    ResponsiveUtils.ScreenSize.LG -> maxOf(4, baseColumns + 3)
                }
            }
            else -> {
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns - 1)
                    ResponsiveUtils.ScreenSize.SM -> baseColumns
                    ResponsiveUtils.ScreenSize.MD -> baseColumns + 1
                    ResponsiveUtils.ScreenSize.LG -> baseColumns + 2
                }
            }
        }
        
        spanCount = columns
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        // Update span count when layout changes (e.g., orientation change)
        updateSpanCount(context)
        super.onLayoutChildren(recycler, state)
    }
}

/**
 * Responsive Staggered Grid Layout Manager
 * Automatically adjusts columns and spacing based on screen size
 */
class ResponsiveStaggeredGridLayoutManager(
    private val context: Context,
    private val baseColumns: Int = 2
) : StaggeredGridLayoutManager(baseColumns, VERTICAL) {

    init {
        updateSpanCount(context)
    }

    private fun updateSpanCount(context: Context) {
        val screenSize = ResponsiveUtils.getScreenSize(context)
        val isLandscape = ResponsiveUtils.isLandscape(context)
        
        val columns = when {
            isLandscape -> {
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns)
                    ResponsiveUtils.ScreenSize.SM -> maxOf(2, baseColumns + 1)
                    ResponsiveUtils.ScreenSize.MD -> maxOf(3, baseColumns + 2)
                    ResponsiveUtils.ScreenSize.LG -> maxOf(4, baseColumns + 3)
                }
            }
            else -> {
                when (screenSize) {
                    ResponsiveUtils.ScreenSize.XS -> maxOf(1, baseColumns - 1)
                    ResponsiveUtils.ScreenSize.SM -> baseColumns
                    ResponsiveUtils.ScreenSize.MD -> baseColumns + 1
                    ResponsiveUtils.ScreenSize.LG -> baseColumns + 2
                }
            }
        }
        
        spanCount = columns
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        // Update span count when layout changes (e.g., orientation change)
        updateSpanCount(context)
        super.onLayoutChildren(recycler, state)
    }
}

/**
 * Extension function to easily apply responsive layout manager to RecyclerView
 */
fun RecyclerView.setResponsiveLayoutManager(
    context: Context,
    baseColumns: Int = 1,
    useStaggered: Boolean = false
) {
    layoutManager = when {
        useStaggered -> ResponsiveStaggeredGridLayoutManager(context, baseColumns)
        baseColumns > 1 -> ResponsiveGridLayoutManager(context, baseColumns)
        else -> ResponsiveLayoutManager.create(context, baseColumns).getLayoutManager()
    }
}
