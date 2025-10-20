package com.example.ecosort.hms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import android.widget.ImageView // Import for the image view
import com.bumptech.glide.Glide // Import for Glide image loading

// Data class to easily handle the data from Firestore for the list item
data class BinListItem(
    val name: String,
    val distance: String,
    val isVerified: Boolean,
    val photoUrl: String // <--- MODIFIED: Added photo URL
)

class RecycleBinAdapter(private val bins: List<BinListItem>) :
    RecyclerView.Adapter<RecycleBinAdapter.BinViewHolder>() {

    class BinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // MODIFIED: Added ImageView reference
        val imgPhoto: ImageView = view.findViewById(R.id.imgBinPhotoList)

        val tvName: TextView = view.findViewById(R.id.tvBinName)
        val tvDistance: TextView = view.findViewById(R.id.tvBinDistance)
        val tvStatus: TextView = view.findViewById(R.id.tvVerificationStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinViewHolder {
        // Inflate the item_recycle_bin.xml layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycle_bin, parent, false)
        return BinViewHolder(view)
    }

    override fun onBindViewHolder(holder: BinViewHolder, position: Int) {
        val bin = bins[position]
        val context = holder.itemView.context

        // 1. IMAGE LOADING LOGIC
        if (bin.photoUrl.isNotEmpty()) {
            Glide.with(context)
                .load(bin.photoUrl)
                .placeholder(R.drawable.ic_placeholder) // Ensure this exists
                .error(R.drawable.ic_launcher_foreground)           // Ensure this exists
                .into(holder.imgPhoto)
        } else {
            // Set a default placeholder if no photo URL is available
            holder.imgPhoto.setImageResource(R.drawable.ic_placeholder)
        }

        // 2. TEXT BINDING LOGIC
        holder.tvName.text = bin.name
        holder.tvDistance.text = bin.distance

        if (bin.isVerified) {
            holder.tvStatus.text = "✅ Verified"
            // Ensure you have R.color.success_green in your colors.xml
            holder.tvStatus.setTextColor(context.getColor(R.color.success_green))
        } else {
            holder.tvStatus.text = "⏳ Pending Verification"
            // Ensure you have R.color.warning_orange in your colors.xml
            holder.tvStatus.setTextColor(context.getColor(R.color.warning_orange))
        }
    }

    override fun getItemCount() = bins.size
}