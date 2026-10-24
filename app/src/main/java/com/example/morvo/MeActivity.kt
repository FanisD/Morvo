package com.example.morvo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.activity_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val english = view.findViewById<RadioButton>(R.id.rbEnglish)
        val greek = view.findViewById<RadioButton>(R.id.rbGreek)
        if (LocaleHelper.getLanguage(requireContext()) == "el") greek.isChecked = true else english.isChecked = true

        view.findViewById<MaterialButton>(R.id.btnSaveFilters).setOnClickListener { saveFilters(view) }
        view.findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSetupActivity::class.java).putExtra("EDIT_MODE", true))
        }
        view.findViewById<MaterialButton>(R.id.btnBlocked).setOnClickListener {
            startActivity(Intent(requireContext(), BlockedUsersActivity::class.java))
        }
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        loadFilters(view)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val view = view ?: return
        val english = view.findViewById<RadioButton>(R.id.rbEnglish)
        val greek = view.findViewById<RadioButton>(R.id.rbGreek)
        if (LocaleHelper.getLanguage(requireContext()) == "el") greek.isChecked = true else english.isChecked = true
        view.findViewById<RadioGroup>(R.id.rgLanguage).setOnCheckedChangeListener { _, checkedId ->
            val language = if (checkedId == R.id.rbGreek) "el" else "en"
            if (language == LocaleHelper.getLanguage(requireContext())) return@setOnCheckedChangeListener
            (activity as? HomeActivity)?.applyLanguage(language)
        }
    }

    private fun loadFilters(view: View) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (!isAdded) return@addOnSuccessListener
            val prefs = doc.get("discoveryPrefs") as? Map<*, *>
            val min = (prefs?.get("ageMin") as? Number)?.toInt() ?: 18
            val max = (prefs?.get("ageMax") as? Number)?.toInt() ?: 99
            view.findViewById<TextInputEditText>(R.id.etAgeMin).setText(min.toString())
            view.findViewById<TextInputEditText>(R.id.etAgeMax).setText(max.toString())
        }
    }

    private fun saveFilters(view: View) {
        val uid = auth.currentUser?.uid ?: return
        val min = view.findViewById<TextInputEditText>(R.id.etAgeMin).text?.toString()?.toIntOrNull()
        val max = view.findViewById<TextInputEditText>(R.id.etAgeMax).text?.toString()?.toIntOrNull()
        if (min == null || max == null || min < 18 || max > 99 || min > max) {
            Toast.makeText(requireContext(), R.string.filters_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = mapOf("ageMin" to min, "ageMax" to max)
        db.collection("users").document(uid)
            .set(mapOf("discoveryPrefs" to prefs), SetOptions.merge())
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), R.string.filters_saved, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(requireContext(), R.string.network_profile_error, Toast.LENGTH_SHORT).show()
            }
    }
}
