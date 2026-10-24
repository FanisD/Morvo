package com.example.morvo

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : MorvoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        setContentView(R.layout.activity_home)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val initial = savedInstanceState?.getInt(EXTRA_TAB)
            ?: intent.getIntExtra(EXTRA_TAB, R.id.nav_classic)

        nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        if (nav.selectedItemId == initial) {
            showTab(initial)
        } else {
            nav.selectedItemId = initial
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val selected = findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId
        outState.putInt(EXTRA_TAB, selected)
    }

    fun applyLanguage(language: String) {
        if (language == LocaleHelper.getLanguage(this)) return
        LocaleHelper.setLanguage(this, language)
        intent.putExtra(EXTRA_TAB, R.id.nav_me)
        recreate()
    }

    private fun showTab(itemId: Int) {
        val tags = listOf(TAG_CLASSIC, TAG_NEARBY, TAG_INBOX, TAG_ME)
        val tag = when (itemId) {
            R.id.nav_nearby -> TAG_NEARBY
            R.id.nav_inbox -> TAG_INBOX
            R.id.nav_me -> TAG_ME
            else -> TAG_CLASSIC
        }
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.anim.nav_fade_in, R.anim.nav_fade_out)
        val current = tags.mapNotNull { supportFragmentManager.findFragmentByTag(it) }
        var target = supportFragmentManager.findFragmentByTag(tag)
        if (target == null) {
            target = fragmentFor(tag)
            transaction.add(R.id.homeContent, target, tag)
        }
        current.forEach { fragment ->
            if (fragment != target) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.show(target)
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED)
        transaction.commit()
    }

    private fun fragmentFor(tag: String): Fragment {
        return when (tag) {
            TAG_NEARBY -> DiscoveryFragment()
            TAG_INBOX -> InboxFragment()
            TAG_ME -> MeFragment()
            else -> ClassicDiscoverFragment()
        }
    }

    companion object {
        const val EXTRA_TAB = "home_tab"
        private const val TAG_CLASSIC = "tab_classic"
        private const val TAG_NEARBY = "tab_nearby"
        private const val TAG_INBOX = "tab_inbox"
        private const val TAG_ME = "tab_me"
    }
}
