package com.bearded.derek.ankicar

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import kotlinx.android.synthetic.main.activity_entry_activity.*

class EntryActivity : BaseActivity() {

    companion object {
        val REQUEST_REVIEW = 1
    }

    private lateinit var pager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_activity)
        setSupportActionBar(toolbar)


        fab.setOnClickListener { view ->
            val intent = Intent(this@EntryActivity, ReviewActivity::class.java)
            startActivityForResult(intent, REQUEST_REVIEW)
        }

        pager = findViewById(R.id.pager)
        pager.adapter = ScreenSliderPagerAdapter(supportFragmentManager)

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

    override fun onBackPressed() {
        if (pager.currentItem == 0) {
            super.onBackPressed()
        } else {
            pager.currentItem = pager.currentItem - 1
        }
    }

    private inner class ScreenSliderPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
//                0 -> CardsFragment()
                0 -> ReviewListFragment()
                1 -> ReviewListDifficultCardsFragment()
                else -> ReviewListFlaggedCardsFragment()
            }
        }

        override fun getCount(): Int = 3

        override fun getPageTitle(position: Int): CharSequence? = when (position) {
//            0 -> "Cards"
            0 -> "Reviewed"
            1 -> "Difficult"
            else -> "Flagged"
        }
    }
}
