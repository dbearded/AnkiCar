package com.bearded.derek.ankicar

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.CardDao
import com.bearded.derek.ankicar.model.DbCard

import kotlinx.android.synthetic.main.activity_entry_activity.*

class EntryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_activity)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val intent = Intent(this@EntryActivity, ReviewActivity::class.java)
            startActivity(intent)
        }

        with(supportFragmentManager.beginTransaction()) {
            add(R.id.card_list, EntryActivityFragment(), "ReviewListFragment")
            commit()
        }
    }
}
