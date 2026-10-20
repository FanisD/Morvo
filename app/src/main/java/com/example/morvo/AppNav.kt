package com.example.morvo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object AppNav {
    fun bind(activity: AppCompatActivity, selectedItemId: Int) {
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        nav.selectedItemId = selectedItemId
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            val destination = when (item.itemId) {
                R.id.nav_classic -> ClassicDiscoverActivity::class.java
                R.id.nav_nearby -> DiscoveryActivity::class.java
                R.id.nav_inbox -> MatchesInboxActivity::class.java
                R.id.nav_me -> MeActivity::class.java
                else -> return@setOnItemSelectedListener false
            }
            activity.startActivity(Intent(activity, destination).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            true
        }
    }
}
