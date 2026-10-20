package com.example.morvo

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView

class MatchesInboxActivity : MorvoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: InboxAdapter
    private val matchPairsList = mutableListOf<Pair<String, String>>() // Holds Pair(MatchId, PartnerUid)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_matches_inbox)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        AppNav.bind(this, R.id.nav_inbox)

        val myUid = auth.currentUser?.uid ?: return

        val rv = findViewById<RecyclerView>(R.id.rvInbox)
        adapter = InboxAdapter(matchPairsList, myUid)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        loadActiveMatches(myUid)
    }

    private fun loadActiveMatches(myUid: String) {
        db.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshots, e ->
                if (snapshots == null || e != null) return@addSnapshotListener

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
                findViewById<TextView>(R.id.tvInboxEmpty).visibility =
                    if (loadedPairs.isEmpty()) View.VISIBLE else View.GONE
                findViewById<RecyclerView>(R.id.rvInbox).visibility =
                    if (loadedPairs.isEmpty()) View.GONE else View.VISIBLE
            }
    }
}