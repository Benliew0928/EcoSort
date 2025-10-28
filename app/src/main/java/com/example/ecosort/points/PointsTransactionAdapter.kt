package com.example.ecosort.points

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import java.text.SimpleDateFormat
import java.util.*

class PointsTransactionAdapter : RecyclerView.Adapter<PointsTransactionAdapter.TransactionViewHolder>() {
    
    private var transactions: List<HashMap<String, Any>> = emptyList()
    
    fun updateTransactions(newTransactions: List<HashMap<String, Any>>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_points_transaction, parent, false)
        return TransactionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }
    
    override fun getItemCount(): Int = transactions.size
    
    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        
        fun bind(transaction: HashMap<String, Any>) {
            val description = transaction["description"]?.toString() ?: "Unknown transaction"
            val points = transaction["points"] as? Long ?: 0L
            val timestamp = transaction["timestamp"] as? Date
            
            tvDescription.text = description
            
            // Format points with + or - sign
            val pointsText = if (points >= 0) "+$points" else "$points"
            tvPoints.text = pointsText
            tvPoints.setTextColor(
                if (points >= 0) {
                    itemView.context.getColor(R.color.primary_green)
                } else {
                    itemView.context.getColor(R.color.error_color)
                }
            )
            
            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            tvTimestamp.text = timestamp?.let { dateFormat.format(it) } ?: "Unknown time"
        }
    }
}