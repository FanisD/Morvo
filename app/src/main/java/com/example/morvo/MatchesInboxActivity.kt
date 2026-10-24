package com.example.morvo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class InboxFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: InboxAdapter
    private var matchesListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.activity_matches_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val myUid = auth.currentUser?.uid ?: return
        val rv = view.findViewById<RecyclerView>(R.id.rvInbox)
        adapter = InboxAdapter(mutableListOf(), myUid)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        loadActiveMatches(view, myUid)
    }

    override fun onDestroyView() {
        matchesListener?.remove()
        matchesListener = null
        super.onDestroyView()
    }

    private fun loadActiveMatches(view: View, myUid: String) {
        matchesListener = db.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshots, e ->
                if (!isAdded || snapshots == null || e != null) return@addSnapshotListener

                val loadedPairs = mutableListOf<Pair<String, String>>()
                snapshots.forEach { doc ->
                    val type = doc.getString("type")
                    val expiresAt = doc.getLong("expiresAt")
                    val stillOpen = type == "classic" || expiresAt == null || expiresAt > System.currentTimeMillis()
                    if (stillOpen) {
                        val users = doc.get("users") as? List<String>
                        val partnerUid = users?.firstOrNull { it != myUid }
                        if (partnerUid != null) {
                            loadedPairs.add(Pair(doc.id, partnerUid))
                        }
                    }
                }
                adapter.updateList(loadedPairs)
                view.findViewById<TextView>(R.id.tvInboxEmpty).visibility =
                    if (loadedPairs.isEmpty()) View.VISIBLE else View.GONE
                view.findViewById<RecyclerView>(R.id.rvInbox).visibility =
                    if (loadedPairs.isEmpty()) View.GONE else View.VISIBLE
            }
    }
}
