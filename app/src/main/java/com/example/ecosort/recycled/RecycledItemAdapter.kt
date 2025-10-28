package com.example.ecosort.recycled

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import java.text.SimpleDateFormat
import java.util.*

class RecycledItemAdapter : RecyclerView.Adapter<RecycledItemAdapter.RecycledItemViewHolder>() {
    
    private var items: List<HashMap<String, Any>> = emptyList()
    
    fun updateItems(newItems: List<HashMap<String, Any>>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecycledItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycled_item, parent, false)
        return RecycledItemViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RecycledItemViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount(): Int = items.size
    
    class RecycledItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvItemName: TextView = itemView.findViewById(R.id.tvItemName)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvBinColor: TextView = itemView.findViewById(R.id.tvBinColor)
        private val tvRecycledDate: TextView = itemView.findViewById(R.id.tvRecycledDate)
        private val tvPointsEarned: TextView = itemView.findViewById(R.id.tvPointsEarned)
        
        fun bind(item: HashMap<String, Any>) {
            tvItemName.text = item["itemName"]?.toString() ?: "Unknown Item"
            tvCategory.text = "Category: ${item["category"]?.toString() ?: "Unknown"}"
            tvBinColor.text = "Bin: ${item["binColor"]?.toString() ?: "Unknown"}"
            
            // Format date
            val recycledDate = item["recycledDate"] as? Date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            tvRecycledDate.text = "Recycled: ${recycledDate?.let { dateFormat.format(it) } ?: "Unknown"}"
            
            // Points earned
            val points = item["pointsEarned"] as? Long ?: 0L
            tvPointsEarned.text = "+$points points"
        }
    }
}