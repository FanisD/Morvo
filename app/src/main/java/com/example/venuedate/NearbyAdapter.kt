package com.example.venuedate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class NearbyAdapter(
    private var users: List<User>,
    private var myHobbies: List<String> = emptyList(),
    private val onProfileClick: (User) -> Unit,
    private val onIntentClick: (User) -> Unit
) : RecyclerView.Adapter<NearbyAdapter.ViewHolder>() {

    // These track the real-time interest states
    private var inboundTaps: Set<String> = emptySet()
    private var outboundTaps: Set<String> = emptySet()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivUserImage)
        val tvName: TextView = view.findViewById(R.id.tvUserName)
        val tvVibe: TextView = view.findViewById(R.id.tvUserContext)
        val tvBadge: TextView = view.findViewById(R.id.tvCompatibility)
        val tvInterestStatus: TextView = view.findViewById(R.id.tvInterestStatus)
        val btnTap: Button = view.findViewById(R.id.btnTap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nearby_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = "${user.firstName}, ${user.age}"
        if (user.locationContext.isNotEmpty()) {
            // Shows both! e.g., "Good Vibes Only • Sitting at the bar"
            holder.tvVibe.text = "${user.vibeTag} • ${user.locationContext}"
        } else {
            // Falls back to just the vibe tag if they didn't type a location
            holder.tvVibe.text = user.vibeTag
        }

        // 1. Evaluate Interaction States
        val hasInbound = inboundTaps.contains(user.uid)
        val hasOutbound = outboundTaps.contains(user.uid)

        holder.btnTap.visibility = View.VISIBLE
        holder.btnTap.text = "Tap"
        holder.tvInterestStatus.visibility = View.GONE

        if (hasInbound) {
            holder.tvInterestStatus.text = "✨ Interested in You!"
            holder.tvInterestStatus.visibility = View.VISIBLE
            holder.btnTap.text = "Match" // Changes button text since tapping them back creates a match
        } else if (hasOutbound) {
            holder.tvInterestStatus.text = "⏳ You are interested in"
            holder.tvInterestStatus.visibility = View.VISIBLE
            holder.btnTap.visibility = View.GONE // Hides the button so you can't double-tap
        }

        // 2. Evaluate Compatibility (Only show if there isn't an overriding Interest status)
        if (!hasInbound && !hasOutbound && myHobbies.isNotEmpty() && user.isCompatibilityModeActive) {
            val sharedCount = user.hobbies.intersect(myHobbies.toSet()).size
            if (sharedCount >= 7) {
                holder.tvBadge.text = "🔥 Top Match ($sharedCount Shared)"
                holder.tvBadge.visibility = View.VISIBLE
            } else {
                holder.tvBadge.visibility = View.GONE
            }
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        // 3. Load Image
        if (user.imageUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.imageUrls[0])
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumb)
        }

        holder.btnTap.setOnClickListener { onIntentClick(user) }

        // 1. Click listener for the specific "Tap" / "Match" button
        holder.btnTap.setOnClickListener { onIntentClick(user) }

        // 2. NEW: Click listener for the entire card to show the profile details!
        holder.itemView.setOnClickListener { onProfileClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    fun updateMyHobbies(hobbies: List<String>) {
        myHobbies = hobbies
        notifyDataSetChanged()
    }

    fun updateInteractionStates(inbound: Set<String>, outbound: Set<String>) {
        this.inboundTaps = inbound
        this.outboundTaps = outbound
        notifyDataSetChanged()
    }
}