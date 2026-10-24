package com.example.morvo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MainActivity : MorvoActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, R.string.google_sign_in_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase App Check
        //FirebaseApp.initializeApp(this)
        //val firebaseAppCheck = FirebaseAppCheck.getInstance()
        //firebaseAppCheck.installAppCheckProviderFactory(
         //   PlayIntegrityAppCheckProviderFactory.getInstance()
       // )
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // --- AUTO-LOGIN REMOVED FROM HERE ---
        // We no longer call checkProfileStatus() automatically in onCreate.
        // This allows you to see the screen and choose your login method.

        // 1. Google Sign-In Button
        findViewById<SignInButton>(R.id.btnGoogle).setOnClickListener {
            // If there's an old session, sign out first so the Google Account Picker pops up
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        // 2. Email Login Button
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etPassword).text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user?.isEmailVerified == true) {
                            checkProfileStatus(user.uid)
                        } else {
                            Toast.makeText(this, R.string.verify_email_first, Toast.LENGTH_SHORT).show()
                            auth.signOut()
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.login_failed, task.exception?.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, R.string.enter_email_password, Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Manual Email Registration
        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
            val pass = findViewById<EditText>(R.id.etPassword).text.toString().trim()

            if (email.isNotEmpty() && pass.length >= 6) {
                auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        auth.currentUser?.sendEmailVerification()
                        Toast.makeText(this, R.string.verification_email_sent, Toast.LENGTH_LONG).show()
                        auth.signOut()
                    } else {
                        Toast.makeText(this, getString(R.string.auth_error, task.exception?.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The loading screen is VISIBLE by default from the XML
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Keep the loading screen up and check their profile!
            checkProfileStatus(currentUser.uid)
        } else {
            // Not logged in? Hide the loading screen so they can type their email
            findViewById<View>(R.id.layoutLoading).visibility = View.GONE
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                checkProfileStatus(auth.currentUser?.uid ?: "")
            }
        }
    }

    private fun checkProfileStatus(uid: String) {
        val userEmail = auth.currentUser?.email ?: ""

        // 1. Check if the user is on the blacklist first
        db.collection("banned_emails").document(userEmail).get().addOnSuccessListener { banDoc ->
            if (banDoc.exists()) {
                // BOOT THEM OUT
                Toast.makeText(this, R.string.account_banned, Toast.LENGTH_LONG).show()
                auth.signOut()
                findViewById<View>(R.id.layoutLoading).visibility = View.GONE
            } else {
                // 2. Normal profile check if they are not banned
                db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                    if (doc.exists() && !doc.getString("firstName").isNullOrEmpty()) {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        startActivity(Intent(this, ProfileSetupActivity::class.java))
                        finish()
                    }
                }.addOnFailureListener {
                    findViewById<View>(R.id.layoutLoading).visibility = View.GONE
                    Toast.makeText(this, R.string.network_profile_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}