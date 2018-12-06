package com.bearded.derek.ankicar

import android.Manifest
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme()

        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }

    fun setTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this@BaseActivity)
        val theme = prefs.getString("theme", "")
        if (theme.equals(resources.getString(R.string.theme_dark))) {
            this.setTheme(R.style.Theme_Dark)
        } else {
            this.setTheme(R.style.Theme_Light)
        }
    }
}