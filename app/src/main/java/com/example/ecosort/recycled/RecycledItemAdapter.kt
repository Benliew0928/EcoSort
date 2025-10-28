package com.example.ecosort.recycled

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.RecycledItem
import java.text.SimpleDateFormat
import java.util.*

class RecycledItemAdapter(
    private val onItemClick: (RecycledItem) -> Unit
) : ListAdapter<RecycledItem, RecycledItemAdapter.RecycledItemViewHolder>(RecycledItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecycledItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycled_item, parent, false)
        return RecycledItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecycledItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecycledItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivItemIcon: ImageView = itemView.findViewById(R.id.ivItemIcon)
        private val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        private val tvItemType: TextView = itemView.findViewById(R.id.tvItemType)
        private val tvRecycledDate: TextView = itemView.findViewById(R.id.tvRecycledDate)
        private val tvPointsEarned: TextView = itemView.findViewById(R.id.tvPointsEarned)
        private val tvWeight: TextView = itemView.findViewById(R.id.tvWeight)

        fun bind(item: RecycledItem) {
            tvItemName.text = item.itemName
            tvItemType.text = item.itemType
            tvPointsEarned.text = "+${item.pointsEarned}"
            
            // Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = Date(item.recycledDate)
            
            val isToday = isToday(item.recycledDate)
            val isYesterday = isYesterday(item.recycledDate)
            
            tvRecycledDate.text = when {
                isToday -> "Today at ${timeFormat.format(date)}"
                isYesterday -> "Yesterday at ${timeFormat.format(date)}"
                else -> dateFormat.format(date)
            }
            
            // Show weight if available
            if (item.weight != null && item.weight > 0) {
                tvWeight.text = "${String.format("%.1f", item.weight)} kg"
                tvWeight.visibility = View.VISIBLE
            } else {
                tvWeight.visibility = View.GONE
            }
            
            // Set item type icon and color
            setItemTypeIcon(item.itemType)
            
            // Set click listener
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
        
        private fun setItemTypeIcon(itemType: String) {
            val (iconRes, colorRes) = when (itemType.lowercase()) {
                "plastic" -> Pair(R.drawable.ic_recycle_32, R.color.primary_color)
                "paper" -> Pair(R.drawable.ic_recycle_32, R.color.accent_color)
                "metal" -> Pair(R.drawable.ic_recycle_32, R.color.text_secondary)
                "glass" -> Pair(R.drawable.ic_recycle_32, R.color.primary_color)
                "organic" -> Pair(R.drawable.ic_recycle_32, R.color.accent_color)
                "electronic" -> Pair(R.drawable.ic_recycle_32, R.color.text_primary)
                else -> Pair(R.drawable.ic_recycle_32, R.color.primary_color)
            }
            
            ivItemIcon.setImageResource(iconRes)
            ivItemIcon.setColorFilter(itemView.context.getColor(colorRes))
        }
        
        private fun isToday(timestamp: Long): Boolean {
            val today = Calendar.getInstance()
            val itemDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            return today.get(Calendar.YEAR) == itemDate.get(Calendar.YEAR) &&
                   today.get(Calendar.DAY_OF_YEAR) == itemDate.get(Calendar.DAY_OF_YEAR)
        }
        
        private fun isYesterday(timestamp: Long): Boolean {
            val yesterday = Calendar.getInstance().apply { 
                add(Calendar.DAY_OF_YEAR, -1) 
            }
            val itemDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            return yesterday.get(Calendar.YEAR) == itemDate.get(Calendar.YEAR) &&
                   yesterday.get(Calendar.DAY_OF_YEAR) == itemDate.get(Calendar.DAY_OF_YEAR)
        }
    }

    class RecycledItemDiffCallback : DiffUtil.ItemCallback<RecycledItem>() {
        override fun areItemsTheSame(oldItem: RecycledItem, newItem: RecycledItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RecycledItem, newItem: RecycledItem): Boolean {
            return oldItem == newItem
        }
    }
}
