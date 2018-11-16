package com.bearded.derek.ankicar

import android.content.Context
import android.os.AsyncTask
import android.support.v4.app.Fragment
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bearded.derek.ankicar.data.CardListAdapter
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.DbCard

/**
 * A placeholder fragment containing a simple view.
 */
class EntryActivityFragment : Fragment() {

    lateinit var adapter: CardListAdapter
    lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AnkiDatabase.getInstance(activity!!.applicationContext)
        adapter = CardListAdapter(db!!)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        adapter.refresh()
    }
}