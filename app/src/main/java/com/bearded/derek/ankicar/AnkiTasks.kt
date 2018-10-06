package com.bearded.derek.ankicar

import android.content.ContentResolver
import android.net.Uri
import android.os.AsyncTask
import android.text.TextUtils
import com.ichi2.anki.FlashCardsContract
import java.lang.ref.WeakReference

class QueryAnkiSchedule(onCompletionListener: OnCompletionListener) : AsyncTask<ContentResolver, Void,
        List<ReviewInfo>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<ReviewInfo>)
    }

    private val EMPTY_MEDA: String = "[]"
    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<ReviewInfo> {
        val cr: ContentResolver = params[0] ?: return emptyList()

        val reviewInfos = mutableListOf<ReviewInfo>()

        val scheduledCardsUri: Uri = FlashCardsContract.ReviewInfo.CONTENT_URI

        val deckSelector = "limit=?"
        val deckArguments = arrayOf("100")

        cr.query(scheduledCardsUri, null, deckSelector, deckArguments,null).use {
            while (it.moveToNext()) {
                if (TextUtils.equals(it.getString(4), EMPTY_MEDA)) {
                    reviewInfos += ReviewInfo(it.getLong(0),
                            it.getLong(1),
                            it.getLong(2),
                            it.getString(3))
                }
            }
        }

        return reviewInfos
    }

    override fun onPostExecute(result: List<ReviewInfo>?) {
        weakReferenceListener.get()?.let { it.onComplete(result ?: emptyList()) }
    }
}

class QueryAnkiSimpleCards(private val reviewInfo: List<ReviewInfo>, onCompletionListener: OnCompletionListener) :
        AsyncTask<ContentResolver, Void, List<ReviewInfo>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<ReviewInfo>)
    }

    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<ReviewInfo> {
        val cr: ContentResolver = params[0] ?: return emptyList()

        var noteUri: Uri
        var cardsUri: Uri
        var specifiCardUri: Uri

        val projection = FlashCardsContract.Card.DEFAULT_PROJECTION + arrayOf(FlashCardsContract.Card.QUESTION_SIMPLE,
                FlashCardsContract.Card.ANSWER_SIMPLE,
                FlashCardsContract.Card.ANSWER_PURE)

        reviewInfo.forEach {
            noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, it.noteId.toString())
            cardsUri = Uri.withAppendedPath(noteUri, "cards")
            specifiCardUri = Uri.withAppendedPath(cardsUri, it.cardOrd.toString())
            cr.query(specifiCardUri,
                    projection,
                    null,
                    null,
                    null).use {
                if (!it.moveToFirst()) return@use
                val noteId = it.getLong(0)
                val cardOrd = it.getLong(1)
                val cardName = it.getString(2)
                val did = it.getString(3)
                val question = it.getString(4)
                val answer = it.getString(5)
                val questionSimple = it.getString(6)
                val answerSimple = it.getString(7)
                val answerPure = it.getString(8)
                val names = it.columnNames
            }
        }

        return emptyList()
    }

    override fun onPostExecute(result: List<ReviewInfo>?) {
        weakReferenceListener.get()?.let { it.onComplete(result ?: emptyList()) }
    }
}