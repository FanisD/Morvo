package com.example.morvo

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class BlockedUsersActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: BlockedUsersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // THIS is what links the Kotlin code to your XML layout to paint the screen!
        setContentView(R.layout.activity_blocked_users)

        findViewById<ImageButton>(R.id.btnBackBlocked).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvBlockedUsers)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = BlockedUsersAdapter(mutableListOf()) { userToUnblock, position ->
            unblockUser(userToUnblock.uid, position)
        }
        rv.adapter = adapter

        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            val blockedUids = user?.blockedUsers ?: emptyList()

            if (blockedUids.isEmpty()) {
                Toast.makeText(this, "You have no blocked users.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Firestore 'whereIn' limits to 10 items per query, so we process the blocklist in chunks
            val blockedUsersList = mutableListOf<User>()
            val chunks = blockedUids.chunked(10)
            var processedChunks = 0

            for (chunk in chunks) {
                db.collection("users").whereIn("uid", chunk).get().addOnSuccessListener { query ->
                    blockedUsersList.addAll(query.toObjects(User::class.java))
                    processedChunks++

                    if (processedChunks == chunks.size) {
                        adapter.updateList(blockedUsersList)
                    }
                }.addOnFailureListener { e ->
                    Log.e("BlockedUsers", "Error fetching blocked users list", e)
                    Toast.makeText(this, "Network error: Could not load blocked users.", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            Log.e("BlockedUsers", "Error fetching my profile", e)
            Toast.makeText(this, "Network error: Could not load profile.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unblockUser(unblockUid: String, position: Int) {
        val myUid = auth.currentUser?.uid ?: return

        // Instantly removes the UID from your blockedUsers array in Firestore
        db.collection("users").document(myUid)
            .update("blockedUsers", FieldValue.arrayRemove(unblockUid))
            .addOnSuccessListener {
                adapter.removeUser(position)
                Toast.makeText(this, "User unblocked", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Log.e("BlockedUsers", "Error unblocking user", e)
                Toast.makeText(this, "Failed to unblock user. Try again.", Toast.LENGTH_SHORT).show()
            }
    }
}