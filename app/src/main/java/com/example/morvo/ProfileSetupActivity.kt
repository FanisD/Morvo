package com.example.morvo

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlin.jvm.java

class ProfileSetupActivity : MorvoActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val imageUris = arrayOfNulls<Uri>(5)
    private val currentUrls = arrayOfNulls<String>(5)
    private var currentSlot = 0
    private val answers = mutableMapOf<String, Any>()
    private var step = 0
    private var isEditMode = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUris[currentSlot] = uri
            val imageViewId = resources.getIdentifier("iv$currentSlot", "id", packageName)
            val imageView = findViewById<ImageView>(imageViewId)

            // 1. Set the newly picked image
            imageView.setImageURI(uri)

            // 2. THE FIX: Explicitly remove the XML imageTintList!
            imageView.imageTintList = null
            imageView.clearColorFilter()
            imageView.background = null

            // 3. Make the photo zoom in perfectly to fill the card
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        isEditMode = intent.getBooleanExtra("EDIT_MODE", false)
        val tvTitle = findViewById<TextView>(R.id.tvProfileTitle)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        val btnLogOut = findViewById<Button>(R.id.btnLogOut)
        val btnDelete = findViewById<Button>(R.id.btnDeleteAccount)
        val btnBlockedUsers = findViewById<Button>(R.id.btnBlockedUsers)

        // Setup Spinners
        val genders = arrayOf("Male", "Female", "Other")
        findViewById<Spinner>(R.id.spinnerGender).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genders)

        val interests = arrayOf("Men", "Women", "Everyone")
        findViewById<Spinner>(R.id.spinnerInterestedIn).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, interests)

        for (i in 0..4) {
            val id = resources.getIdentifier("iv$i", "id", packageName)
            findViewById<ImageView>(id).setOnClickListener {
                currentSlot = i
                pickImageLauncher.launch("image/*")
            }
        }

        if (isEditMode) {
            tvTitle.text = getString(R.string.edit_profile_title)
            btnLogOut.visibility = View.VISIBLE
            btnDelete.visibility = View.VISIBLE
            btnBlockedUsers.visibility = View.VISIBLE
            loadExistingData()
        }
        showStep()

        btnBlockedUsers.setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        findViewById<Button>(R.id.btnBackStep).setOnClickListener {
            if (step > 0) {
                captureCurrentStep()
                step--
                showStep()
            }
        }

        btnSave.setOnClickListener { onContinue() }

        btnLogOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        btnDelete.setOnClickListener { confirmAndDeleteAccount() }
    }

    private fun onContinue() {
        if (step == 0) {
            if (!basicsAreValid()) return
            step = 1
            showStep()
            return
        }
        if (!captureCurrentStep()) {
            Toast.makeText(this, R.string.answer_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (step < ProfileCatalog.sections.size) {
            step++
            showStep()
        } else {
            val name = findViewById<EditText>(R.id.etFirstName).text.toString().trim()
            val age = findViewById<EditText>(R.id.etAge).text.toString().trim().toIntOrNull() ?: 0
            val city = findViewById<EditText>(R.id.etCity).text.toString().trim()
            uploadAllPhotos(name, age, city)
        }
    }

    private fun basicsAreValid(): Boolean {
        val name = findViewById<EditText>(R.id.etFirstName).text.toString().trim()
        val ageStr = findViewById<EditText>(R.id.etAge).text.toString().trim()
        val city = findViewById<EditText>(R.id.etCity).text.toString().trim()
        val hasMainPhoto = imageUris[0] != null || !currentUrls[0].isNullOrEmpty()
        val age = ageStr.toIntOrNull() ?: 0
        if (name.isEmpty() || ageStr.isEmpty() || city.isEmpty() || !hasMainPhoto) {
            Toast.makeText(this, R.string.profile_required, Toast.LENGTH_SHORT).show()
            return false
        }
        if (age < 18) {
            Toast.makeText(this, R.string.age_gate, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun showStep() {
        val total = ProfileCatalog.sections.size + 1
        findViewById<TextView>(R.id.tvStep).text = getString(R.string.wizard_step, step + 1, total)
        findViewById<View>(R.id.basicsGroup).visibility = if (step == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.questionStep).visibility = if (step == 0) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnBackStep).visibility = if (step == 0) View.GONE else View.VISIBLE
        val last = step == ProfileCatalog.sections.size
        findViewById<Button>(R.id.btnSaveProfile).text = when {
            last && isEditMode -> getString(R.string.save_changes)
            last -> getString(R.string.save_and_enter)
            else -> getString(R.string.wizard_next)
        }
        if (step == 0) {
            findViewById<TextView>(R.id.tvProfileTitle).text =
                getString(if (isEditMode) R.string.edit_profile_title else R.string.build_profile)
        } else {
            val section = ProfileCatalog.sections[step - 1]
            findViewById<TextView>(R.id.tvProfileTitle).text = getString(section.titleRes)
            renderSection(section)
        }
    }

    private fun renderSection(section: ProfileCatalog.Section) {
        val container = findViewById<LinearLayout>(R.id.questionStep)
        container.removeAllViews()
        section.questions.forEach { question ->
            val prompt = TextView(this)
            prompt.text = getString(question.promptRes)
            prompt.setTextColor(getColor(R.color.text_primary))
            prompt.textSize = 16f
            prompt.setPadding(0, 28, 0, 8)
            container.addView(prompt)
            if (question.hintRes != 0) {
                val hint = TextView(this)
                hint.text = getString(question.hintRes)
                hint.setTextColor(getColor(R.color.text_secondary))
                container.addView(hint)
            }
            when {
                question.sliders.isNotEmpty() -> renderSliders(container, question)
                question.maxSelect > 1 -> renderMulti(container, question)
                else -> renderSingle(container, question)
            }
        }
    }

    private fun renderSingle(container: LinearLayout, question: ProfileCatalog.Question) {
        val group = RadioGroup(this)
        group.tag = question.key
        val saved = answers[question.key] as? String
        question.options.forEach { option ->
            val radio = RadioButton(this)
            radio.id = View.generateViewId()
            radio.tag = option.key
            radio.text = getString(option.labelRes)
            group.addView(radio)
            if (option.key == saved) radio.isChecked = true
        }
        container.addView(group)
    }

    private fun renderMulti(container: LinearLayout, question: ProfileCatalog.Question) {
        val saved = (answers[question.key] as? List<*>)?.mapNotNull { it as? String }?.toMutableSet() ?: mutableSetOf()
        val boxHolder = LinearLayout(this)
        boxHolder.orientation = LinearLayout.VERTICAL
        boxHolder.tag = question.key
        question.options.forEach { option ->
            val box = CheckBox(this)
            box.tag = option.key
            box.text = getString(option.labelRes)
            box.isChecked = option.key in saved
            box.setOnCheckedChangeListener { _, isChecked ->
                val checked = (0 until boxHolder.childCount).count {
                    (boxHolder.getChildAt(it) as CheckBox).isChecked
                }
                if (isChecked && checked > question.maxSelect) {
                    box.isChecked = false
                    Toast.makeText(this, getString(R.string.pick_limit, question.maxSelect), Toast.LENGTH_SHORT).show()
                }
            }
            boxHolder.addView(box)
        }
        container.addView(boxHolder)
    }

    private fun renderSliders(container: LinearLayout, question: ProfileCatalog.Question) {
        val saved = ProfileAnswers.sliders(mapOf(question.key to (answers[question.key] ?: emptyMap<String, Any>())), question.key)
        question.sliders.forEach { spec ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, 12, 0, 12)
            val labels = TextView(this)
            labels.text = "${getString(spec.leftRes)}  –  ${getString(spec.rightRes)}"
            labels.setTextColor(getColor(R.color.text_secondary))
            val slider = Slider(this)
            slider.tag = spec.key
            slider.valueFrom = 0f
            slider.valueTo = 100f
            slider.stepSize = 1f
            slider.value = (saved[spec.key] ?: 50).toFloat()
            row.addView(labels)
            row.addView(slider)
            container.addView(row)
        }
    }

    private fun captureCurrentStep(): Boolean {
        if (step == 0) return true
        val section = ProfileCatalog.sections[step - 1]
        val container = findViewById<LinearLayout>(R.id.questionStep)
        section.questions.forEach { question ->
            when {
                question.sliders.isNotEmpty() -> {
                    val values = question.sliders.associate { spec ->
                        val slider = container.findViewWithTag<Slider>(spec.key)
                        spec.key to (slider?.value?.toInt() ?: 50)
                    }
                    answers[question.key] = values
                }
                question.maxSelect > 1 -> {
                    val holder = container.findViewWithTag<LinearLayout>(question.key) ?: return false
                    val selected = (0 until holder.childCount).mapNotNull {
                        val box = holder.getChildAt(it) as CheckBox
                        if (box.isChecked) box.tag as String else null
                    }
                    if (selected.isEmpty() || selected.size > question.maxSelect) return false
                    answers[question.key] = selected
                }
                else -> {
                    val group = container.findViewWithTag<RadioGroup>(question.key) ?: return false
                    val selectedId = group.checkedRadioButtonId
                    if (selectedId == -1) return false
                    answers[question.key] = group.findViewById<RadioButton>(selectedId).tag as String
                }
            }
        }
        return true
    }

    private fun loadExistingData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)?.withProfileAnswers(doc) ?: return@addOnSuccessListener

            findViewById<EditText>(R.id.etFirstName).setText(user.firstName)
            findViewById<EditText>(R.id.etAge).setText(user.age.toString())
            findViewById<EditText>(R.id.etCity).setText(user.city)
            findViewById<EditText>(R.id.etOccupation).setText(user.occupation)

            setSpinnerSelection(R.id.spinnerGender, user.gender)
            setSpinnerSelection(R.id.spinnerInterestedIn, user.interestedIn)
            answers.clear()
            answers.putAll(user.profileAnswers)

            // Load existing images into the layout
            user.imageUrls.forEachIndexed { index, url ->
                if (index < 5) {
                    currentUrls[index] = url
                    val imageViewId = resources.getIdentifier("iv$index", "id", packageName)
                    val imageView = findViewById<ImageView>(imageViewId)

                    // Force remove the tint and background for existing downloaded photos too
                    imageView.imageTintList = null // MUST have this for app:tint
                    imageView.clearColorFilter()
                    imageView.background = null

                    Glide.with(this)
                        .load(url)
                        // THE FIX: Tell Glide to skip the cache and always fetch the newest photo
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .centerCrop()
                        .into(imageView)
                }
            }
        }
    }

    private fun setSpinnerSelection(spinnerId: Int, value: String) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString() == value) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun uploadAllPhotos(name: String, age: Int, city: String) {
        val uid = auth.currentUser?.uid ?: return
        val indexesToUpload = mutableListOf<Int>()

        // Check which specific slots have brand new images
        for (i in 0..4) {
            if (imageUris[i] != null) indexesToUpload.add(i)
        }

        // If they didn't pick any new images, jump straight to saving text data
        if (indexesToUpload.isEmpty()) {
            saveUserData(name, age, city)
            return
        }

        var completed = 0
        indexesToUpload.forEach { i ->
            val ref = storage.reference.child("profiles/$uid/img_$i.jpg")
            ref.putFile(imageUris[i]!!).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    currentUrls[i] = downloadUri.toString() // Replace the old URL with the new one
                    completed++
                    if (completed == indexesToUpload.size) {
                        saveUserData(name, age, city)
                    }
                }
            }
        }
    }

    private fun saveUserData(name: String, age: Int, city: String) {
        val uid = auth.currentUser?.uid ?: return

        // Strip out any nulls or blank slots
        val finalUrls = currentUrls.filterNotNull().filter { it.isNotEmpty() }

        // REMOVED "email" FROM THIS MAP
        val userMap = hashMapOf(
            "uid" to uid,
            "firstName" to name,
            "age" to age,
            "gender" to findViewById<Spinner>(R.id.spinnerGender).selectedItem.toString(),
            "interestedIn" to findViewById<Spinner>(R.id.spinnerInterestedIn).selectedItem.toString(),
            "city" to city,
            "occupation" to findViewById<EditText>(R.id.etOccupation).text.toString().trim(),
            "imageUrls" to finalUrls,
            "profileAnswers" to answers.toMap(),
            "hobbies" to FieldValue.delete(),
            "vibeTag" to FieldValue.delete(),
            "isCompatibilityModeActive" to FieldValue.delete()
        )

        // 1. Save the public data
        db.collection("users").document(uid).set(userMap, SetOptions.merge()).addOnSuccessListener {

            // 2. Save the private email securely
            val privateData = hashMapOf("email" to auth.currentUser?.email)
            db.collection("users").document(uid)
                .collection("private").document("contact")
                .set(privateData, SetOptions.merge())
                .addOnSuccessListener {

                    // Proceed as normal
                    if (isEditMode) {
                        Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish() // Standard onboarding forward progression
                    }
                }
        }
    }

    private fun confirmAndDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_account_title))
            .setMessage(getString(R.string.delete_account_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val user = auth.currentUser
                val uid = user?.uid ?: return@setPositiveButton

                Toast.makeText(this, R.string.deleting_account, Toast.LENGTH_LONG).show()

                // 1. Delete Storage Photos (These can safely run in the background)
                for (i in 0..4) {
                    storage.reference.child("profiles/$uid/img_$i.jpg").delete()
                }

                // Create a batch to execute all database deletions simultaneously
                val batch = db.batch()

                // 2. Find Matches
                db.collection("matches").whereArrayContains("users", uid).get().addOnSuccessListener { matchSnap ->
                    for (doc in matchSnap.documents) {
                        batch.delete(doc.reference)
                    }

                    // 3. Find Outbound Taps (nested to ensure proper order)
                    db.collectionGroup("taps").whereEqualTo("from", uid).get().addOnSuccessListener { fromSnap ->
                        for (doc in fromSnap.documents) {
                            batch.delete(doc.reference)
                        }

                        // 4. Find Inbound Taps
                        db.collectionGroup("taps").whereEqualTo("to", uid).get().addOnSuccessListener { toSnap ->
                            for (doc in toSnap.documents) {
                                batch.delete(doc.reference)
                            }

                            // 5. Add Main User Profile to the batch
                            val userRef = db.collection("users").document(uid)
                            batch.delete(userRef)

                            // 6. COMMIT THE BATCH: Delete everything at the exact same time
                            batch.commit().addOnCompleteListener {

                                // 7. ONLY AFTER the database is clean, we delete the Auth and sign out
                                user.delete().addOnCompleteListener {
                                    auth.signOut()

                                    // Redirect to login screen
                                    startActivity(Intent(this, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    })
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}