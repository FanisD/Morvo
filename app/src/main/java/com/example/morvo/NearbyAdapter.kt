package com.example.morvo

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
    private var me: User = User(),
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
        holder.tvVibe.text = user.city

        val hasInbound = inboundTaps.contains(user.uid)
        val hasOutbound = outboundTaps.contains(user.uid)

        holder.btnTap.visibility = View.VISIBLE
        holder.btnTap.text = holder.itemView.context.getString(R.string.tap)
        holder.tvInterestStatus.visibility = View.GONE
        val percent = CompatibilityCalculator.percent(me, user)
        holder.tvBadge.visibility = View.VISIBLE
        holder.tvBadge.text = holder.itemView.context.getString(R.string.compatibility_percent, percent)

        if (hasInbound) {
            holder.tvInterestStatus.text = holder.itemView.context.getString(R.string.interested_in_you)
            holder.tvInterestStatus.visibility = View.VISIBLE
            holder.btnTap.text = holder.itemView.context.getString(R.string.match_button)
        } else if (hasOutbound) {
            holder.tvInterestStatus.text = holder.itemView.context.getString(R.string.you_are_interested)
            holder.tvInterestStatus.visibility = View.VISIBLE
            holder.btnTap.visibility = View.GONE
        }

        if (user.imageUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(user.imageUrls[0])
                .circleCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivThumb)
        }

        // 4. Click Listeners
        holder.btnTap.setOnClickListener { onIntentClick(user) }
        holder.itemView.setOnClickListener { onProfileClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
    }

    fun updateMe(user: User) {
        me = user
        notifyDataSetChanged()
    }

    fun updateInteractionStates(inbound: Set<String>, outbound: Set<String>) {
        this.inboundTaps = inbound
        this.outboundTaps = outbound
        notifyDataSetChanged()
    }
}