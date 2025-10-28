package com.example.ecosort.points

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.PointsTransaction
import com.example.ecosort.data.model.PointsTransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTransactionAdapter(
    private val onTransactionClick: (PointsTransaction) -> Unit
) : ListAdapter<PointsTransaction, PointsTransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_points_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivTransactionIcon: ImageView = itemView.findViewById(R.id.ivTransactionIcon)
        private val tvTransactionDescription: TextView = itemView.findViewById(R.id.tvTransactionDescription)
        private val tvTransactionSource: TextView = itemView.findViewById(R.id.tvTransactionSource)
        private val tvTransactionDate: TextView = itemView.findViewById(R.id.tvTransactionDate)
        private val tvPointsAmount: TextView = itemView.findViewById(R.id.tvPointsAmount)

        fun bind(transaction: PointsTransaction) {
            // Set transaction icon based on type
            when (transaction.type) {
                PointsTransactionType.EARNED -> {
                    ivTransactionIcon.setImageResource(R.drawable.ic_trending_up)
                    ivTransactionIcon.setColorFilter(itemView.context.getColor(R.color.success_color))
                }
                PointsTransactionType.SPENT -> {
                    ivTransactionIcon.setImageResource(R.drawable.ic_trending_down)
                    ivTransactionIcon.setColorFilter(itemView.context.getColor(R.color.error_color))
                }
                PointsTransactionType.BONUS -> {
                    ivTransactionIcon.setImageResource(R.drawable.ic_trophy)
                    ivTransactionIcon.setColorFilter(itemView.context.getColor(R.color.accent_color))
                }
                PointsTransactionType.REFUND -> {
                    ivTransactionIcon.setImageResource(R.drawable.ic_refresh)
                    ivTransactionIcon.setColorFilter(itemView.context.getColor(R.color.primary_color))
                }
            }

            // Set transaction details
            tvTransactionDescription.text = transaction.description
            tvTransactionSource.text = transaction.source.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }

            // Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvTransactionDate.text = dateFormat.format(Date(transaction.timestamp))

            // Set points amount with appropriate color and sign
            val pointsText = if (transaction.amount > 0) "+${transaction.amount}" else transaction.amount.toString()
            tvPointsAmount.text = pointsText
            
            val colorRes = when (transaction.type) {
                PointsTransactionType.EARNED, PointsTransactionType.BONUS -> R.color.success_color
                PointsTransactionType.SPENT, PointsTransactionType.REFUND -> R.color.error_color
            }
            tvPointsAmount.setTextColor(itemView.context.getColor(colorRes))

            // Set click listener
            itemView.setOnClickListener { onTransactionClick(transaction) }
        }
    }

    class TransactionDiffCallback : DiffUtil.ItemCallback<PointsTransaction>() {
        override fun areItemsTheSame(oldItem: PointsTransaction, newItem: PointsTransaction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PointsTransaction, newItem: PointsTransaction): Boolean {
            return oldItem == newItem
        }
    }
}
