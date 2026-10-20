package com.example.morvo

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MeActivity : MorvoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_me)
        AppNav.bind(this, R.id.nav_me)

        val english = findViewById<RadioButton>(R.id.rbEnglish)
        val greek = findViewById<RadioButton>(R.id.rbGreek)
        if (LocaleHelper.getLanguage(this) == "el") greek.isChecked = true else english.isChecked = true

        findViewById<RadioGroup>(R.id.rgLanguage).setOnCheckedChangeListener { _, checkedId ->
            val language = if (checkedId == R.id.rbGreek) "el" else "en"
            if (language == LocaleHelper.getLanguage(this)) return@setOnCheckedChangeListener
            LocaleHelper.setLanguage(this, language)
            val restart = Intent(this, MeActivity::class.java)
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(restart)
        }

        findViewById<MaterialButton>(R.id.btnSaveFilters).setOnClickListener { saveFilters() }
        findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSetupActivity::class.java).putExtra("EDIT_MODE", true))
        }
        findViewById<MaterialButton>(R.id.btnBlocked).setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        loadFilters()
    }

    private fun loadFilters() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val prefs = doc.get("discoveryPrefs") as? Map<*, *>
            val min = (prefs?.get("ageMin") as? Number)?.toInt() ?: 18
            val max = (prefs?.get("ageMax") as? Number)?.toInt() ?: 99
            findViewById<TextInputEditText>(R.id.etAgeMin).setText(min.toString())
            findViewById<TextInputEditText>(R.id.etAgeMax).setText(max.toString())
        }
    }

    private fun saveFilters() {
        val uid = auth.currentUser?.uid ?: return
        val min = findViewById<TextInputEditText>(R.id.etAgeMin).text?.toString()?.toIntOrNull()
        val max = findViewById<TextInputEditText>(R.id.etAgeMax).text?.toString()?.toIntOrNull()
        if (min == null || max == null || min < 18 || max > 99 || min > max) {
            Toast.makeText(this, R.string.filters_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = mapOf("ageMin" to min, "ageMax" to max)
        db.collection("users").document(uid)
            .set(mapOf("discoveryPrefs" to prefs), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, R.string.filters_saved, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.network_profile_error, Toast.LENGTH_SHORT).show()
            }
    }
}
