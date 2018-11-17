package com.bearded.derek.ankicar

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_entry_activity.*

class EntryActivity : BaseActivity() {

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

        if (supportFragmentManager.findFragmentById(R.id.card_list) == null) {
            with(supportFragmentManager.beginTransaction()) {
                add(R.id.card_list, EntryActivityFragment(), "ReviewListFragment")
                commit()
            }
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_entry_acitivty, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when (item.itemId) {
            R.id.theme_toggle -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@EntryActivity)
                val typedValue = TypedValue()
                theme.resolveAttribute(R.attr.theme_dependent_theme_icon, typedValue, true)
                if (typedValue.resourceId == R.drawable.ic_theme_dark) {
                    prefs.edit().putString("theme", resources.getString(R.string.theme_dark)).commit()
                } else {
                    prefs.edit().putString("theme", resources.getString(R.string.theme_light)).commit()
                }
                recreate()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
