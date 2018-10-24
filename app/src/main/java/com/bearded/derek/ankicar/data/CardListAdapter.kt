package com.bearded.derek.ankicar.data

import android.content.Context
import android.os.AsyncTask
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bearded.derek.ankicar.R
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.DbCard

class CardListAdapter(private val db: AnkiDatabase) : RecyclerView.Adapter<CardListAdapter.CardViewHolder>() {

    private var cards = emptyList<DbCard>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        return CardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_item, parent, false))
    }

    override fun getItemCount(): Int {
        return cards.size
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.question.text = cards[position].question
        holder.answer.text = cards[position].answer
        holder.cardOrd.text = cards[position].cardOrd.toString()
        holder.ease.text = cards[position].ease.toString()
    }

    fun refresh() {
        val task = object : AsyncTask<AnkiDatabase, Void, List<DbCard>>() {
            override fun doInBackground(vararg params: AnkiDatabase?): List<DbCard> {
                params[0]?.let {
                    return it.cardDao().getAll().sortedBy {
                        it.time
                    }.asReversed()
                }

                return emptyList()
            }

            override fun onPostExecute(result: List<DbCard>?) {
                cards = result!!
                notifyDataSetChanged()
            }
        }
        task.execute(db)
    }

    inner class CardViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val question: TextView = itemView.findViewById(R.id.question)
        val answer: TextView = itemView.findViewById(R.id.answer)
        val cardOrd: TextView = itemView.findViewById(R.id.card_ord)
        val ease: TextView = itemView.findViewById(R.id.ease)
    }
}