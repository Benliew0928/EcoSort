package com.example.ecosort.hms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import android.widget.ImageView // Import for the image view
import com.bumptech.glide.Glide // Import for Glide image loading

import androidx.core.content.ContextCompat


// Data class to easily handle the data from Firestore for the list item
data class BinListItem(
    val name: String,
    val distance: String,
    val isVerified: Boolean,
    val photoUrl: String, // <--- MODIFIED: Added photo URL
    val latitude: Double,    // ⭐ ADD THIS LINE ⭐
    val longitude: Double    // ⭐ ADD THIS LINE ⭐
)

interface OnBinItemClickListener {
    // Pass the required data for the map action
    fun onBinItemClick(latitude: Double, longitude: Double, photoUrl: String, clickedIndex: Int) // ⭐ ADD clickedIndex ⭐
}

class RecycleBinAdapter(
    // ⭐ CHANGE 'val' TO 'var' HERE ⭐
    private var bins: List<BinListItem>,
    private val listener: OnBinItemClickListener,
    private val isAdmin: Boolean
) : RecyclerView.Adapter<RecycleBinAdapter.BinViewHolder>() {

    class BinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // MODIFIED: Added ImageView reference
        val imgPhoto: ImageView = view.findViewById(R.id.imgBinPhotoList)

        val tvName: TextView = view.findViewById(R.id.tvBinName)
        val tvDistance: TextView = view.findViewById(R.id.tvBinDistance)
        val tvStatus: TextView = view.findViewById(R.id.tvVerificationStatus)


        val container: View = view.findViewById(R.id.bin_item_container) // ⭐ Ensure this ID exists in your item XML ⭐
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

        // --- 1. IMAGE LOADING LOGIC ---
        if (bin.photoUrl.isNotEmpty()) {
            Glide.with(context)
                .load(bin.photoUrl)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.app_logo)
                .into(holder.imgPhoto)
        } else {
            holder.imgPhoto.setImageResource(R.drawable.ic_placeholder)
        }

        // --- 2. TEXT BINDING LOGIC ---
        holder.tvName.text = bin.name
        holder.tvDistance.text = bin.distance

        if (bin.isVerified) {
            holder.tvStatus.text = "✅ Verified"
            holder.tvStatus.setTextColor(context.getColor(R.color.success_green))
        } else {
            holder.tvStatus.text = "⏳ Pending Verification"
            holder.tvStatus.setTextColor(context.getColor(R.color.warning_orange))
        }

        // --- 3. HIGHLIGHTING LOGIC ---
        // Use ContextCompat for reliable color retrieval
        val highlightColor = ContextCompat.getColor(context, R.color.light_olive)
        val defaultColor = ContextCompat.getColor(context, R.color.light_bg) // Assuming dark_olive is your default

        if (position == selectedPosition) {
            holder.container.setBackgroundColor(highlightColor)
        } else {
            holder.container.setBackgroundColor(defaultColor)
        }

        // --- 4. CLICK LISTENER (Only attached once) ---
        holder.itemView.setOnClickListener {
            // This is crucial for *un-highlighting* the previously selected item
            listener.onBinItemClick(bin.latitude, bin.longitude, bin.photoUrl, position)
        }
    }

    override fun getItemCount() = bins.size


    private var selectedPosition = RecyclerView.NO_POSITION


    // NEW: Public method to update selection from MapActivity
    fun setSelected(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position

        // Notify the adapter to efficiently redraw only the affected items
        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }
        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition)
        }
    }

    fun updateList(newList: List<BinListItem>, selectedIndex: Int) {
        // 1. Update the internal list reference
        (bins as? MutableList)?.clear() // Ensure the original list is mutable if needed, or reassign
        // A safer way is to ensure 'bins' is defined as a var list
        // If 'bins' is currently defined as: 'private val bins: List<BinListItem>'
        // You should change it to: 'private var bins: List<BinListItem>'

        // Assuming you change it to 'private var bins: List<BinListItem>':
        this.bins = newList

        // 2. Update the selected position
        selectedPosition = selectedIndex

        // 3. Redraw the entire list
        notifyDataSetChanged()
    }
}