package com.example.morvo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ClassicDiscoverFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var me: User = User()
    private var deck: List<User> = emptyList()
    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.activity_classic_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        findViewById<MaterialButton>(R.id.btnPass).setOnClickListener { currentUser()?.let { pass(it) } }
        findViewById<MaterialButton>(R.id.btnLike).setOnClickListener { currentUser()?.let { like(it) } }
    }

    private fun <T : View> findViewById(id: Int): T = requireView().findViewById(id)

    override fun onResume() {
        super.onResume()
        loadDeck()
    }

    private fun currentUser(): User? = deck.firstOrNull()

    private fun loadDeck() {
        val myUid = auth.currentUser?.uid ?: return
        setStatus(getString(R.string.classic_loading))

        db.collection("users").document(myUid).get().addOnSuccessListener { meDoc ->
            val loadedMe = meDoc.toObject(User::class.java)?.withProfileAnswers(meDoc)
            if (loadedMe == null) {
                setStatus(getString(R.string.classic_empty))
                return@addOnSuccessListener
            }
            me = loadedMe
            val prefs = meDoc.get("discoveryPrefs") as? Map<*, *>
            val ageMin = (prefs?.get("ageMin") as? Number)?.toInt() ?: 18
            val ageMax = (prefs?.get("ageMax") as? Number)?.toInt() ?: 99

            db.collection("swipes").document(myUid).collection("liked").get()
                .addOnSuccessListener { likedSnap ->
                    val liked = likedSnap.documents.map { it.id }.toSet()
                    db.collection("swipes").document(myUid).collection("passed").get()
                        .addOnSuccessListener { passedSnap ->
                            val passed = passedSnap.documents.map { it.id }.toSet()
                            db.collection("matches").whereArrayContains("users", myUid).get()
                                .addOnSuccessListener { matchSnap ->
                                    val matched = matchSnap.documents.mapNotNull { doc ->
                                        (doc.get("users") as? List<*>)
                                            ?.filterIsInstance<String>()
                                            ?.firstOrNull { it != myUid }
                                    }.toSet()
                                    db.collection("users").limit(80).get()
                                        .addOnSuccessListener { peopleSnap ->
                                            deck = peopleSnap.documents.mapNotNull { doc ->
                                                val person = doc.toObject(User::class.java)?.withProfileAnswers(doc) ?: return@mapNotNull null
                                                if (person.uid.isBlank()) person.copy(uid = doc.id) else person
                                            }
                                                .filter { person ->
                                                    person.uid.isNotBlank() &&
                                                        person.uid != myUid &&
                                                        person.firstName.isNotBlank() &&
                                                        person.uid !in liked &&
                                                        person.uid !in passed &&
                                                        person.uid !in matched &&
                                                        person.uid !in me.blockedUsers &&
                                                        myUid !in person.blockedUsers &&
                                                        person.age in ageMin..ageMax &&
                                                        InterestFilter.matches(
                                                            me.gender,
                                                            me.interestedIn,
                                                            person.gender,
                                                            person.interestedIn
                                                        )
                                                }
                                            showNext()
                                        }
                                        .addOnFailureListener { setStatus(getString(R.string.network_profile_error)) }
                                }
                        }
                }
        }.addOnFailureListener { setStatus(getString(R.string.network_profile_error)) }
    }

    private fun showNext() {
        val person = deck.firstOrNull()
        val card = findViewById<View>(R.id.cardProfile)
        val actions = findViewById<View>(R.id.actionRow)
        if (person == null) {
            card.visibility = View.GONE
            actions.visibility = View.GONE
            setStatus(getString(R.string.classic_empty))
            return
        }

        findViewById<TextView>(R.id.tvEmpty).visibility = View.GONE
        card.visibility = View.VISIBLE
        actions.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvName).text = "${person.firstName}, ${person.age}"
        val city = if (person.city.isNotBlank()) person.city else getString(R.string.location_hidden)
        val job = if (person.occupation.isNotBlank()) person.occupation else getString(R.string.not_specified)
        findViewById<TextView>(R.id.tvDetails).text = "$city · $job"
        val percent = CompatibilityCalculator.percent(me, person)
        findViewById<TextView>(R.id.tvPercent).text = getString(R.string.compatibility_percent, percent)

        val photo = findViewById<ImageView>(R.id.ivPhoto)
        if (person.imageUrls.isNotEmpty()) {
            Glide.with(this).load(person.imageUrls[0]).centerCrop().into(photo)
        } else {
            photo.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        setBusy(false)
    }

    private fun setStatus(message: String) {
        val empty = findViewById<TextView>(R.id.tvEmpty)
        empty.visibility = View.VISIBLE
        empty.text = message
    }

    private fun pass(person: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (busy) return
        setBusy(true)
        db.collection("swipes").document(myUid).collection("passed").document(person.uid)
            .set(mapOf("from" to myUid, "to" to person.uid, "timestamp" to System.currentTimeMillis()))
            .addOnSuccessListener { advance() }
            .addOnFailureListener {
                setBusy(false)
                Toast.makeText(requireContext(), R.string.swipe_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun like(person: User) {
        val myUid = auth.currentUser?.uid ?: return
        if (busy) return
        setBusy(true)
        db.collection("swipes").document(myUid).collection("liked").document(person.uid)
            .set(mapOf("from" to myUid, "to" to person.uid, "timestamp" to System.currentTimeMillis()))
            .addOnSuccessListener {
                db.collection("swipes").document(person.uid).collection("liked").document(myUid)
                    .get()
                    .addOnSuccessListener { theirLike ->
                        if (theirLike.exists()) createClassicMatch(person) else advance()
                    }
                    .addOnFailureListener { advance() }
            }
            .addOnFailureListener {
                setBusy(false)
                Toast.makeText(requireContext(), R.string.swipe_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun createClassicMatch(person: User) {
        val myUid = auth.currentUser?.uid ?: return
        val matchId = if (myUid < person.uid) "${myUid}_${person.uid}" else "${person.uid}_${myUid}"
        val data = hashMapOf(
            "users" to listOf(myUid, person.uid),
            "timestamp" to System.currentTimeMillis(),
            "type" to "classic"
        )
        db.collection("matches").document(matchId).set(data, SetOptions.merge())
            .addOnSuccessListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.its_a_match)
                    .setMessage(getString(R.string.classic_match_message, person.firstName))
                    .setPositiveButton(R.string.start_chat) { _, _ ->
                        startActivity(Intent(requireContext(), ChatActivity::class.java).putExtra("MATCH_ID", matchId))
                        advance()
                    }
                    .setNegativeButton(R.string.keep_browsing) { _, _ -> advance() }
                    .setCancelable(false)
                    .show()
            }
            .addOnFailureListener {
                setBusy(false)
                Toast.makeText(requireContext(), R.string.swipe_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun advance() {
        deck = if (deck.isEmpty()) deck else deck.drop(1)
        showNext()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        findViewById<MaterialButton>(R.id.btnPass).isEnabled = !value
        findViewById<MaterialButton>(R.id.btnLike).isEnabled = !value
    }
}
