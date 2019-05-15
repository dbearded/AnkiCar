package com.bearded.derek.ankicar.data

import android.os.AsyncTask
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bearded.derek.ankicar.R
import com.bearded.derek.ankicar.model.AnkiDatabase
import com.bearded.derek.ankicar.model.DbCard
import com.bearded.derek.ankicar.model.Review
import java.text.SimpleDateFormat
import java.util.*

class ReviewListAdapter(private val db: AnkiDatabase) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Item>()

    val startDateFormat = SimpleDateFormat("MM/dd HH:mm")
    val endDateFormat = SimpleDateFormat("HH:mm")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        when (viewType) {
            Item.TYPE_HEADER -> {
                return HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false))
            }
            Item.TYPE_CARD -> {
                return CardViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.card_item, parent, false))
            }
            else -> return ReviewViewHolder(parent) // Unreachable - here to satisfy Kotlin
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return items[position].getType()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            Item.TYPE_HEADER -> {
                val review = (items[position] as Header).review
                val range: String = startDateFormat.format(review.startDate) + " - " + endDateFormat.format(review.endDate)
                (holder as HeaderViewHolder).header.text = range
            }

            Item.TYPE_CARD -> {
                val cardHolder = holder as CardViewHolder
                with((items[position] as Card).card) {
                    cardHolder.question.text = question
                    cardHolder.answer.text = answer
                    cardHolder.cardOrd.text = cardOrd.toString()
                    cardHolder.ease.text = ease.toString()
                }
            }
        }
    }

    fun <D>refresh(getDao: AnkiDatabase.() -> D, daoMethod: D.(Date, Date) -> List<DbCard>, transformation:
    Iterable<DbCard>.() -> List<DbCard>) {
        val task = object : AsyncTask<AnkiDatabase, Void, List<Item>>() {
            override fun doInBackground(vararg params: AnkiDatabase?): List<Item> {
                val db = params[0] ?: return emptyList()

                val reviewDao = db.reviewDao()

                val reviewList = reviewDao.getAll()
                val reviewSorted = reviewList.sortedBy { it.endDate }
                val reversed = reviewSorted.asReversed()

                val items = mutableListOf<Item>()
                reversed.forEach {
                    val cards = db.getDao().daoMethod(it.startDate, it.endDate).transformation().map {
                        Card(it)
                    }
                    if (cards.isNotEmpty()) {
                        items.add(Header(it))
                        items.addAll(cards)
                    }
                }

                return items
            }

            override fun onPostExecute(result: List<Item>?) {
                items.clear()
                result?.let {
                    items += it
                }
                notifyDataSetChanged()
            }
        }

        task.execute(db)
    }

    open inner class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class HeaderViewHolder(itemView: View) : ReviewViewHolder(itemView) {
        val header: TextView = itemView.findViewById(R.id.review_header)
    }

    inner class CardViewHolder(itemView: View): ReviewViewHolder(itemView) {
        val question: TextView = itemView.findViewById(R.id.question)
        val answer: TextView = itemView.findViewById(R.id.answer)
        val cardOrd: TextView = itemView.findViewById(R.id.card_num)
        val ease: TextView = itemView.findViewById(R.id.ease)
    }

    abstract class Item() {
        companion object {
            val TYPE_HEADER = 0
            val TYPE_CARD = 1
        }

        abstract fun getType() : Int
    }

    class Header(val review: Review) : Item() {
        override fun getType(): Int {
            return TYPE_HEADER
        }
    }

    class Card(val card: DbCard) : Item() {
        override fun getType(): Int {
            return TYPE_CARD
        }
    }
}