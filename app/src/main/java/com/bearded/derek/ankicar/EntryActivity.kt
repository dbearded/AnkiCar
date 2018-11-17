package com.bearded.derek.ankicar

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.CardDao
import com.bearded.derek.ankicar.model.DbCard

import kotlinx.android.synthetic.main.activity_entry_activity.*

class EntryActivity : AppCompatActivity() {

    companion object {

        val REQUEST_REVIEW = 1

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_activity)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            val intent = Intent(this@EntryActivity, ReviewActivity::class.java)
            startActivityForResult(intent, REQUEST_REVIEW)

            // For deleting tables
//            val task = object : AsyncTask<Void, Void, Boolean>() {
//                override fun doInBackground(vararg params: Void?): Boolean {
//                    return AnkiDatabase.clearAndResetAllTables()
//                }
//
//                override fun onPostExecute(result: Boolean?) {
//                    Toast.makeText(this@EntryActivity, "Tables deleted", Toast.LENGTH_SHORT).show()
//                }
//            }
//            task.execute()
        }

        with(supportFragmentManager.beginTransaction()) {
            add(R.id.card_list, EntryActivityFragment(), "ReviewListFragment")
            commit()
        }
    }

    // Not working yet
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EntryActivity.REQUEST_REVIEW) {
            val fragment = supportFragmentManager.findFragmentById(R.id.card_list) as EntryActivityFragment?
            fragment?.adapter?.notifyDataSetChanged()
        }
    }
}
