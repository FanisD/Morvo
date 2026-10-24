package com.example.morvo

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

open class MorvoActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
