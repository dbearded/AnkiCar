package com.bearded.derek.ankicar

import android.content.Context
import android.os.AsyncTask
import android.support.v4.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.DbCard

/**
 * A placeholder fragment containing a simple view.
 */
class EntryActivityFragment : Fragment() {


    lateinit var question: TextView
    lateinit var answer: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_entry_activity, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        question = view.findViewById(R.id.question)
        answer = view.findViewById(R.id.answer)

        val db = AnkiDatabase.getInstance(activity as Context)

        val task = object : AsyncTask<AnkiDatabase, Void, List<DbCard>>() {
            override fun doInBackground(vararg params: AnkiDatabase?): List<DbCard> {
                db?.let {
                    val dao = it.cardDao()
                    return dao.getAll()
                }

                return emptyList()
            }

            override fun onPostExecute(result: List<DbCard>?) {
                result?.let {
                    question.text = it[0].question
                    answer.text = it[0].answer
                }
            }
        }

        task.execute(db)
    }
}