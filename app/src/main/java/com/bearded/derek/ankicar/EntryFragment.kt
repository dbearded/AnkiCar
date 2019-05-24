package com.bearded.derek.ankicar

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bearded.derek.ankicar.data.ReviewListAdapter
import com.bearded.derek.ankicar.data.VerticalSpaceItemDecorator
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.CardDao

// silly comment
/**
 * A placeholder fragment containing a simple view.
 */
class ReviewListFragment : Fragment() {

    lateinit var adapter: ReviewListAdapter
    lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AnkiDatabase.getInstance(activity!!.applicationContext)
        adapter = ReviewListAdapter(db!!)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.addItemDecoration(VerticalSpaceItemDecorator.buildDecorator(16, 16, 16, 0,
                activity!!.resources.displayMetrics.density))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        adapter.refresh(AnkiDatabase::cardDao, CardDao::getAllBetweenDates) {
            sortedByDescending {
                it.date
            }
        }
    }
}

//class CardsFragment : Fragment() {
//    lateinit var adapter: ReviewListAdapter
//    lateinit var recyclerView: RecyclerView
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
//                              savedInstanceState: Bundle?): View? {
//        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        val db = AnkiDatabase.getInstance(activity!!.applicationContext)
//        adapter = ReviewListAdapter(db!!)
//        recyclerView = view.findViewById(R.id.recycler_view)
//        recyclerView.addItemDecoration(VerticalSpaceItemDecorator.buildDecorator(16, 16, 16, 0,
//            activity!!.resources.displayMetrics.density))
//        recyclerView.adapter = adapter
//        recyclerView.layoutManager = LinearLayoutManager(activity)
//
//        adapter.refreshCards()
//    }
//}

class ReviewListDifficultCardsFragment : Fragment() {
    lateinit var adapter: ReviewListAdapter
    lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AnkiDatabase.getInstance(activity!!.applicationContext)
        adapter = ReviewListAdapter(db!!)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.addItemDecoration(VerticalSpaceItemDecorator.buildDecorator(16, 16, 16, 0,
                activity!!.resources.displayMetrics.density))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        adapter.refresh(AnkiDatabase::cardDao, { start, stop ->
            getEaseBetweenDates(start, stop, 1, 2)
        }) {
            sortedByDescending {
                it.date
            }
        }
    }
}

class ReviewListFlaggedCardsFragment : Fragment() {
    lateinit var adapter: ReviewListAdapter
    lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AnkiDatabase.getInstance(activity!!.applicationContext)
        adapter = ReviewListAdapter(db!!)
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.addItemDecoration(VerticalSpaceItemDecorator.buildDecorator(16, 16, 16, 0,
                activity!!.resources.displayMetrics.density))
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(activity)

        adapter.refresh(AnkiDatabase::cardDao, CardDao::getFlaggedBetweenDates) {
            sortedByDescending {
                it.date
            }
        }
    }
}
