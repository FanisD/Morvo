package com.example.venuedate

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DiscoveryActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var adapter: NearbyAdapter
    private var radarListener: ListenerRegistration? = null
    private var matchListener: ListenerRegistration? = null
    private var inboundTapListener: ListenerRegistration? = null
    private var outboundTapListener: ListenerRegistration? = null
    private var globalChatListener: ListenerRegistration? = null

    // Real-time cached lists
    private val rawNearbyUsers = mutableListOf<User>()
    private val activeMatches = mutableMapOf<String, Long>()
    private val inboundTaps = mutableMapOf<String, Long>()
    private val outboundTaps = mutableMapOf<String, Long>()

    private val channelId = "venue_date_radar"
    private val appSessionStartTime = System.currentTimeMillis()
    private val notifiedCompatibleUsers = mutableSetOf<String>()

    private var myHobbies = listOf<String>()
    private var myBlockedUsers = listOf<String>()
    private var myGender = ""
    private var myInterestedIn = ""
    private var isCompatibilityModeActive = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) checkLocationPermission() else findViewById<SwitchMaterial>(R.id.switchAmHere).isChecked = false
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        startLiveStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NEW: Check SharedPreferences for the saved theme before drawing the UI
        val prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", 0) // 0 is System Default
        val mode = when (savedTheme) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        setContentView(R.layout.activity_discovery)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        adapter = NearbyAdapter(
            users = emptyList(),
            myHobbies = myHobbies,
            onProfileClick = { selectedUser -> showProfileDetailsSheet(selectedUser) },
            onIntentClick = { selectedUser -> sendInterest(selectedUser) }
        )
        val rv = findViewById<RecyclerView>(R.id.rvNearby)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val slider = findViewById<Slider>(R.id.rangeSlider)
        val tvRange = findViewById<TextView>(R.id.tvRangeLabel)

        // Map slider positions (0, 1, 2) to exact distances (50, 100, 200)
        val rangeOptions = intArrayOf(50, 100, 200)

        slider.addOnChangeListener { _, value, _ ->
            val selectedMeters = rangeOptions[value.toInt()]
            tvRange.text = "Search Range: ${selectedMeters}m"
        }

        findViewById<SwitchMaterial>(R.id.switchAmHere).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkLocationPermission() else stopLiveStatus()
        }

        val switchComp = findViewById<SwitchMaterial>(R.id.switchCompatibilityMode)
        switchComp.setOnCheckedChangeListener { _, isChecked ->
            isCompatibilityModeActive = isChecked
            auth.currentUser?.uid?.let { uid ->
                db.collection("users").document(uid).update("isCompatibilityModeActive", isChecked)
            }
            refreshRadarUI()
        }

        findViewById<ImageButton>(R.id.btnInbox).setOnClickListener {
            startActivity(Intent(this, MatchesInboxActivity::class.java))
        }

        // Start real-time environment listeners
        listenForMatches()
        listenForTaps()
        listenForGlobalMessages()

        val btnProfileMenu = findViewById<View>(R.id.btnProfileMenu)
        btnProfileMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_profile -> {
                        val intent = Intent(this, ProfileSetupActivity::class.java)
                        intent.putExtra("EDIT_MODE", true)
                        startActivity(intent)
                        true
                    }
                    R.id.action_appearance -> {
                        showAppearanceDialog()
                        true
                    }
                    R.id.action_logout -> {
                        auth.signOut()
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkUserHobbiesEligibility()
    }

    private fun checkUserHobbiesEligibility() {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            if (user != null) {
                myHobbies = user.hobbies
                myBlockedUsers = user.blockedUsers
                myGender = user.gender
                myInterestedIn = user.interestedIn
                val switchComp = findViewById<SwitchMaterial>(R.id.switchCompatibilityMode)

                if (myHobbies.size >= 10) {
                    switchComp.isEnabled = true
                    switchComp.text = "Compatibility Matching Mode (Active)"
                } else {
                    switchComp.isEnabled = false
                    switchComp.isChecked = false
                    switchComp.text = "Compatibility Mode Locked (${myHobbies.size}/10 Hobbies Picked)"
                }
                adapter.updateMyHobbies(myHobbies)

                if (user.imageUrls.isNotEmpty()) {
                    val ivUserProfilePic = findViewById<ImageView>(R.id.ivUserProfilePic)
                    Glide.with(this)
                        .load(user.imageUrls[0])
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .circleCrop()
                        .into(ivUserProfilePic)
                }

                // Triggers a list refresh now that blocked users are fetched
                refreshRadarUI()
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startLiveStatus()
    }

    private fun startLiveStatus() {
        val uid = auth.currentUser?.uid ?: return

        // Translate the current slider position when activating the live radar
        val rangeIndex = findViewById<Slider>(R.id.rangeSlider).value.toInt()
        val rangeMeters = intArrayOf(50, 100, 200)[rangeIndex]

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val updates = hashMapOf<String, Any>(
                    "isAvailable" to true,
                    "lastLat" to fuzzLocation(location.latitude),
                    "lastLng" to fuzzLocation(location.longitude),
                    "availableUntil" to System.currentTimeMillis() + (20 * 60 * 1000),
                    "isCompatibilityModeActive" to isCompatibilityModeActive
                )
                db.collection("users").document(uid).update(updates).addOnSuccessListener {
                    listenForNearbyUsers(location.latitude, location.longitude, rangeMeters)
                }
            }
        }
    }

    // LISTENER 1: Tracks raw nearby users
    private fun listenForNearbyUsers(myLat: Double, myLng: Double, rangeMeters: Int) {
        radarListener?.remove()

        radarListener = db.collection("users")
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                // Get the exact time right now
                val currentTime = System.currentTimeMillis()
                rawNearbyUsers.clear()

                snapshots?.forEach { doc ->
                    val user = doc.toObject(User::class.java)

                    // NEW: Make sure they aren't ourselves, AND their 20-minute timer is still strictly active!
                    if (user != null && user.uid != auth.currentUser?.uid && user.availableUntil > currentTime) {

                        val dist = FloatArray(1)
                        Location.distanceBetween(myLat, myLng, user.lastLat, user.lastLng, dist)

                        if (dist[0] <= rangeMeters) {
                            rawNearbyUsers.add(user)
                        }
                    }
                }
                refreshRadarUI()
            }
    }

    // LISTENER 2: Tracks Matches to hide them from the radar
    private fun listenForMatches() {
        val myUid = auth.currentUser?.uid ?: return
        matchListener = db.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                val currentTime = System.currentTimeMillis()

                snapshots?.documentChanges?.forEach { dc ->
                    val users = dc.document["users"] as? List<*>
                    val stringUsers = users?.filterIsInstance<String>() ?: emptyList()
                    val partnerUid = stringUsers.firstOrNull { it != myUid }
                    val expiresAt = dc.document.getLong("expiresAt") ?: 0L

                    if (partnerUid != null) {
                        if (dc.type == DocumentChange.Type.ADDED || dc.type == DocumentChange.Type.MODIFIED) {

                            // Check if the match is still valid
                            if (expiresAt > currentTime) {
                                activeMatches[partnerUid] = expiresAt

                                val matchTimestamp = dc.document.getLong("timestamp") ?: 0L
                                if (matchTimestamp > appSessionStartTime && dc.type == DocumentChange.Type.ADDED) {
                                    showMatchOverlay(partnerUid, dc.document.id)
                                }
                            } else {
                                // The match is already expired!
                                activeMatches.remove(partnerUid)
                                val matchId = dc.document.id

                                // Delete the old Tap data
                                db.collection("interests").document(matchId).collection("taps").document(myUid).delete()

                                // Actually delete the match document from the database!
                                db.collection("matches").document(matchId).delete()
                            }

                        } else if (dc.type == DocumentChange.Type.REMOVED) {
                            activeMatches.remove(partnerUid)
                        }
                    }
                }
                refreshRadarUI()
            }
    }

    // LISTENER 3: Tracks Taps (Both Inbound and Outbound) to manage badges
    private fun listenForTaps() {
        val myUid = auth.currentUser?.uid ?: return

        // 1. Inbound Taps
        inboundTapListener = db.collectionGroup("taps").whereEqualTo("to", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                snapshots.documentChanges.forEach { dc ->
                    val senderUid = dc.document.getString("from") ?: return@forEach
                    val timestamp = dc.document.getLong("timestamp") ?: 0L // Get the time

                    if (dc.type == DocumentChange.Type.ADDED || dc.type == DocumentChange.Type.MODIFIED) {
                        inboundTaps[senderUid] = timestamp // Save time instead of just the UID

                        if (dc.type == DocumentChange.Type.ADDED && !snapshots.metadata.hasPendingWrites() && senderUid != myUid) {
                            db.collection("users").document(senderUid).get().addOnSuccessListener { uDoc ->
                                val senderName = uDoc.getString("firstName") ?: "Someone"
                                triggerLocalNotification(senderName)
                            }
                        }
                    } else if (dc.type == DocumentChange.Type.REMOVED) {
                        inboundTaps.remove(senderUid)
                    }
                }
                refreshRadarUI()
            }

        // 2. Outbound Taps
        outboundTapListener = db.collectionGroup("taps").whereEqualTo("from", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                snapshots.documentChanges.forEach { dc ->
                    val targetUid = dc.document.getString("to") ?: return@forEach
                    val timestamp = dc.document.getLong("timestamp") ?: 0L

                    if (dc.type == DocumentChange.Type.ADDED || dc.type == DocumentChange.Type.MODIFIED) {
                        outboundTaps[targetUid] = timestamp
                    } else if (dc.type == DocumentChange.Type.REMOVED) {
                        outboundTaps.remove(targetUid)
                    }
                }
                refreshRadarUI()
            }
    }

    // CENTRAL UI ENGINE: Filters, Sorts, and Renders
    private fun refreshRadarUI() {
        val myUid = auth.currentUser?.uid ?: return
        val currentTime = System.currentTimeMillis()

        // 1 HOUR EXPIRATION TIME
        val tapExpirationLimit = 60 * 60 * 1000L

        // Clean up expired Matches
        val expiredUids = activeMatches.filterValues { it <= currentTime }.keys.toList()
        expiredUids.forEach { expiredUid ->
            activeMatches.remove(expiredUid)
            val matchId = if (myUid < expiredUid) "${myUid}_${expiredUid}" else "${expiredUid}_${myUid}"

            db.collection("interests").document(matchId).collection("taps").document(myUid).delete()

            // Actually delete the match document from the database!
            db.collection("matches").document(matchId).delete()
        }

        // Clean up expired Inbound Taps
        val expiredInbound = inboundTaps.filterValues { currentTime - it > tapExpirationLimit }.keys.toList()
        expiredInbound.forEach { senderUid ->
            inboundTaps.remove(senderUid)
            val matchId = if (myUid < senderUid) "${myUid}_${senderUid}" else "${senderUid}_${myUid}"
            db.collection("interests").document(matchId).collection("taps").document(senderUid).delete()
        }

        // Clean up expired Outbound Taps
        val expiredOutbound = outboundTaps.filterValues { currentTime - it > tapExpirationLimit }.keys.toList()
        expiredOutbound.forEach { targetUid ->
            outboundTaps.remove(targetUid)
            val matchId = if (myUid < targetUid) "${myUid}_${targetUid}" else "${targetUid}_${myUid}"
            db.collection("interests").document(matchId).collection("taps").document(myUid).delete()
        }

        // 1. Filter out blocked users, active matches, enforce Compatibility Mode, AND enforce Gender Preferences
        val validUsers = rawNearbyUsers.filter { user ->

            // Basic safety checks
            val isClean = !activeMatches.containsKey(user.uid) &&
                    !myBlockedUsers.contains(user.uid) &&
                    !user.blockedUsers.contains(myUid)

            // HARD FILTER: Gender & Preference Match
            val isGenderMatch = isMutuallyInterested(myGender, myInterestedIn, user.gender, user.interestedIn)

            // HARD FILTER: If mode is ON, hide anyone who isn't a Super Match
            val passesCompatibility = if (isCompatibilityModeActive) {
                val sharedCount = user.hobbies.intersect(myHobbies.toSet()).size
                user.isCompatibilityModeActive && sharedCount >= 7
            } else {
                true
            }

            isClean && isGenderMatch && passesCompatibility
        }

        // 2. Sort the list by priority ranking
        val sortedUsers = validUsers.sortedWith { user1, user2 ->
            val score1 = calculateSortScore(user1)
            val score2 = calculateSortScore(user2)
            score2.compareTo(score1)
        }

        // 3. Update the view (Pass only the keys/UIDs to the adapter)
        adapter.updateInteractionStates(inboundTaps.keys, outboundTaps.keys)
        adapter.updateList(sortedUsers)
    }

    // SORTING ALGORITHM
    private fun calculateSortScore(user: User): Int {
        // Priority 1: They tapped you (Requires your attention immediately)
        if (inboundTaps.containsKey(user.uid)) return 4

        // Priority 2: You tapped them (Keep them near top so you can see status)
        if (outboundTaps.containsKey(user.uid)) return 3

        // Priority 3: High Compatibility (Super Match)
        if (isCompatibilityModeActive && user.isCompatibilityModeActive) {
            val sharedCount = user.hobbies.intersect(myHobbies.toSet()).size
            if (sharedCount >= 7) {
                if (!notifiedCompatibleUsers.contains(user.uid)) {
                    notifiedCompatibleUsers.add(user.uid)
                    triggerLocalNotification("High Compatibility! ${user.firstName} is nearby with $sharedCount shared interests!")
                }
                return 2
            }
        }

        // Priority 4: Standard user nearby
        return 1
    }

    private fun sendInterest(targetUser: User) {
        val myUid = auth.currentUser?.uid ?: return
        val targetUid = targetUser.uid
        val matchId = if (myUid < targetUid) "${myUid}_${targetUid}" else "${targetUid}_${myUid}"

        val interestData = hashMapOf(
            "from" to myUid,
            "to" to targetUid,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("interests").document(matchId)
            .collection("taps").document(myUid).set(interestData)
            .addOnSuccessListener { checkForMatch(matchId, targetUser) }
    }

    private fun checkForMatch(matchId: String, targetUser: User) {
        val myUid = auth.currentUser?.uid ?: return

        db.collection("interests").document(matchId).collection("taps").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.size() >= 2) {
                    val matchData = hashMapOf(
                        "users" to listOf(myUid, targetUser.uid),
                        "timestamp" to System.currentTimeMillis(),
                        "expiresAt" to System.currentTimeMillis() + (20 * 60 * 1000)
                    )
                    db.collection("matches").document(matchId).set(matchData)
                } else {
                    Toast.makeText(this, "Interest sent to ${targetUser.firstName}!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun triggerLocalNotification(name: String) {
        val intent = Intent(this, DiscoveryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Interest!")
            .setContentText("$name is interested in your profile right now!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Radar Activity Alerts"
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showMatchOverlay(partnerUid: String, matchId: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.match_overlay)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val ivPartner = dialog.findViewById<ImageView>(R.id.ivMatchUser2)
        val ivMe = dialog.findViewById<ImageView>(R.id.ivMatchUser1)
        val btnChat = dialog.findViewById<Button>(R.id.btnStartChat)
        val btnDismiss = dialog.findViewById<Button>(R.id.btnMatchDismiss)

        db.collection("users").document(partnerUid).get().addOnSuccessListener { doc ->
            val partner = doc.toObject(User::class.java)
            if (partner?.imageUrls?.isNotEmpty() == true) {
                Glide.with(this).load(partner.imageUrls[0]).circleCrop().into(ivPartner)
            }
        }

        auth.currentUser?.uid?.let { myUid ->
            db.collection("users").document(myUid).get().addOnSuccessListener { doc ->
                val me = doc.toObject(User::class.java)
                if (me?.imageUrls?.isNotEmpty() == true) {
                    Glide.with(this).load(me.imageUrls[0]).circleCrop().into(ivMe)
                }
            }
        }

        btnChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply { putExtra("MATCH_ID", matchId) }
            startActivity(intent)
            dialog.dismiss()
        }

        btnDismiss.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun stopLiveStatus() {
        radarListener?.remove()
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("isAvailable", false)

        // Wipe the memory clean and trigger a UI refresh
        rawNearbyUsers.clear()
        refreshRadarUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        radarListener?.remove()
        matchListener?.remove()
        inboundTapListener?.remove()
        outboundTapListener?.remove()
        globalChatListener?.remove()
    }

    private fun listenForGlobalMessages() {
        val myUid = auth.currentUser?.uid ?: return
        globalChatListener = db.collectionGroup("messages")
            .whereEqualTo("receiverId", myUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                if (snapshots.metadata.isFromCache) return@addSnapshotListener

                snapshots.documentChanges.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val timestamp = dc.document.getLong("timestamp") ?: 0L
                        val senderId = dc.document.getString("senderId") ?: ""
                        val messageText = dc.document.getString("text") ?: "Sent a message"

                        if (timestamp > appSessionStartTime && senderId != myUid) {
                            triggerLocalNotification("Your match: $messageText")
                        }
                    }
                }
            }
    }

    private fun fuzzLocation(coordinate: Double): Double {
        return Math.round(coordinate * 1000.0) / 1000.0
    }

    private fun showProfileDetailsSheet(user: User) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.dialog_profile_details)

        // Bind UI Elements
        val vpPhotos = bottomSheetDialog.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpProfilePhotos)
        val tvNameAge = bottomSheetDialog.findViewById<TextView>(R.id.tvSheetNameAge)
        val tvCity = bottomSheetDialog.findViewById<TextView>(R.id.tvSheetCity)
        val tvOccupation = bottomSheetDialog.findViewById<TextView>(R.id.tvSheetOccupation)
        val tvGenderInfo = bottomSheetDialog.findViewById<TextView>(R.id.tvSheetGenderInfo)
        val tvHobbies = bottomSheetDialog.findViewById<TextView>(R.id.tvSheetHobbies)

        // Populate Data
        tvNameAge?.text = "${user.firstName}, ${user.age}"
        tvCity?.text = if (user.city.isNotEmpty()) user.city else "Location hidden"
        tvOccupation?.text = "💼 ${if (user.occupation.isNotEmpty()) user.occupation else "Not specified"}"
        tvGenderInfo?.text = "👤 ${user.gender} | Looking for: ${user.interestedIn}"

        tvHobbies?.text = if (user.hobbies.isNotEmpty()) {
            user.hobbies.joinToString(separator = " • ")
        } else {
            "No interests added."
        }

        // Load all images into the ViewPager adapter
        if (user.imageUrls.isNotEmpty() && vpPhotos != null) {
            vpPhotos.adapter = ProfilePhotosAdapter(user.imageUrls)
        } else if (vpPhotos != null) {
            // Fallback if they somehow have no photos
            vpPhotos.visibility = View.GONE
        }

        bottomSheetDialog.show()
    }

    private fun showAppearanceDialog() {
        val options = arrayOf("System Default", "Light", "Dark")
        val prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)

        // Get the current choice so the dialog checks the right bubble
        val currentChoice = prefs.getInt("theme_mode", 0)

        AlertDialog.Builder(this)
            .setTitle("Choose Theme")
            .setSingleChoiceItems(options, currentChoice) { dialog, which ->

                // Map their choice to the correct Android Theme mode
                val mode = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                // Apply the theme instantly
                AppCompatDelegate.setDefaultNightMode(mode)

                // Save their choice permanently to the device memory
                prefs.edit().putInt("theme_mode", which).apply()

                dialog.dismiss()
            }
            .show()
    }

    // Nested adapter to handle the swipable photo gallery in the Bottom Sheet
    private inner class ProfilePhotosAdapter(private val photos: List<String>) :
        RecyclerView.Adapter<ProfilePhotosAdapter.PhotoViewHolder>() {

        inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = layoutInflater.inflate(R.layout.item_profile_photo, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            Glide.with(this@DiscoveryActivity)
                .load(photos[position])
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.ivPhoto)
        }

        override fun getItemCount() = photos.size
    }

    // GENDER & PREFERENCE MATCHER
    private fun isMutuallyInterested(myGender: String, myPreference: String, theirGender: String, theirPreference: String): Boolean {

        // Helper function to normalize "Men/Male" and "Women/Female" for easy comparison
        fun matches(preference: String, gender: String): Boolean {
            if (preference.equals("Everyone", ignoreCase = true)) return true
            if (preference.equals("Men", ignoreCase = true) && gender.equals("Male", ignoreCase = true)) return true
            if (preference.equals("Women", ignoreCase = true) && gender.equals("Female", ignoreCase = true)) return true

            // Fallback just in case the database strictly uses matching words
            return preference.equals(gender, ignoreCase = true)
        }

        val iLikeThem = matches(myPreference, theirGender)
        val theyLikeMe = matches(theirPreference, myGender)

        return iLikeThem && theyLikeMe
    }
}