package com.bearded.derek.ankicar.model.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard.AnkiCardForReview
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard.AnkiCardReviewed
import com.bearded.derek.ankicar.model.anki.Card.Companion.build
import com.bearded.derek.ankicar.utils.Logger
import com.ichi2.anki.FlashCardsContract

interface CardCompletionListener {
    fun onQueryComplete(cards: List<Card>)
    fun onUpdateComplete(numUpdated: Int)
}

const val EMPTY_MEDIA: String = "[]"

fun queryReviewCards(deckId: Long, limit: Int, contentResolver: ContentResolver, callback: CardCompletionListener) {
    Logger.log("AnkiTasks: queryForReviewCards entered - limit: $limit")
    val querySchedule = QueryAnkiSchedule(deckId, limit, object : QueryAnkiSchedule.OnCompletionListener {
        override fun onComplete(reviewInfo: List<AnkiCardForReview>) {
            Logger.log("AnkiTasks: QueryAnkiSchedule.onComplete entered - ankiCardForReview size: ${reviewInfo.size}")
            val querySpecificCards = QueryAnkiSpecificSimpleCards(reviewInfo, object : QueryAnkiSpecificSimpleCards.OnCompletionListener {
                override fun onComplete(reviewInfo: List<AnkiCard>) {
                    Logger.log("AnkiTasks: QueryAnkiSpecificSimpleCards.onComplete entered - ankiCard size: " +
                        "${reviewInfo.size}")
                    val queryAnkyModels = QueryAnkiModels(reviewInfo, object : QueryAnkiModels.OnCompletionListener {
                        override fun onComplete(reviewInfo: List<AnkiCard>) {
                            Logger.log("AnkiTasks: QueryAnkiModels.onComplete entered - ankiCard size: " +
                                "${reviewInfo.size}")
                            val cards = mutableListOf<Card>()
                            reviewInfo.forEach {
                                cards += it.build(it.getCleanser())
                            }

                            callback.onQueryComplete(cards)
                        }
                    })
                    queryAnkyModels.execute(contentResolver)
                }
            })
            querySpecificCards.execute(contentResolver)
        }
    })
    querySchedule.execute(contentResolver)
}

suspend fun queryReviewCards2(deckId: Long, limit: Int, contentResolver: ContentResolver): List<Card> {
    Logger.log("AnkiTasks: queryForReviewCards2 entered - limit: $limit")
    val reviewInfo = queryAnkiSchedule(deckId, limit, contentResolver)

    Logger.log("AnkiTasks: queryAnkiSchedule - ankiCardForReview size: ${reviewInfo.size}")
    val ankiCards = queryAnkiSpecificSimpleCards(reviewInfo, contentResolver)

    Logger.log("AnkiTasks: queryAnkiSpecificSimpleCards entered - ankiCard size: ${ankiCards.size}")
    queryAnkiModels(ankiCards, contentResolver)

    return ankiCards.map {
        it.build(it.getCleanser())
    }
}

fun updateAnki(reviewedCard: AnkiCardReviewed, contentResolver: ContentResolver, callback: CardCompletionListener) {
    Logger.log("AnkiTasks: updateAnki entered - reviewedCard: ${reviewedCard.noteId}")
    val updateSchedule = UpdateAnkiSchedule(reviewedCard, object : UpdateAnkiSchedule.OnCompletionListener {
        override fun onComplete(numUpdated: Int) {
            Logger.log("AnkiTasks: UpdateAnkiSchedule.onComplete entered - numUpdated: $numUpdated")
            callback.onUpdateComplete(numUpdated)
        }
    })
    updateSchedule.execute(contentResolver)
}

suspend fun updateAnki2(reviewedCard: AnkiCardReviewed, contentResolver: ContentResolver): Int {
    Logger.log("AnkiTasks: updateAnki2 entered - reviewedCard: ${reviewedCard.noteId}")
    return updateAnkiSchedule(reviewedCard, contentResolver)
}

class QueryAnkiSchedule(val deckId: Long, val limit: Int, val onCompletionListener: OnCompletionListener) :
    AsyncTask<ContentResolver, Void,
        List<AnkiCardForReview>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<AnkiCardForReview>)
    }

    private val EMPTY_MEDIA: String = "[]"
//    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<AnkiCardForReview> {
        Logger.log("AnkiTasks: QueryAnkiSchedule: doInBackground entered")
        val cr: ContentResolver = params[0] ?: return emptyList()

        val reviewInfos = mutableListOf<AnkiCardForReview>()

        val scheduledCardsUri: Uri = FlashCardsContract.ReviewInfo.CONTENT_URI

        val deckSelector = if (deckId == -1L) {
            "limit=?"
        } else {
            "limit=?, deckID=?, "
        }

        val deckArguments = if (deckId == -1L) {
            arrayOf(limit.toString())
        } else {
            arrayOf(limit.toString(), deckId.toString())
        }

        cr.query(scheduledCardsUri, null, deckSelector, deckArguments, null)?.use {
            while (it.moveToNext()) {
                reviewInfos += AnkiCardForReview(it.getLong(0),
                    it.getInt(1),
                    it.getInt(2),
                    it.getString(3),
                    !TextUtils.equals(it.getString(4), EMPTY_MEDIA))
            }
        }

        return reviewInfos
    }

    override fun onPostExecute(result: List<AnkiCardForReview>?) {
        Logger.log("AnkiTasks: QueryAnkiSchedule: onPostExecute entered")
//        weakReferenceListener.get()?.onComplete(result ?: emptyList())
        onCompletionListener.onComplete(result ?: emptyList())
    }
}

suspend fun queryAnkiSchedule(deckId: Long, limit: Int, contentResolver: ContentResolver): List<AnkiCardForReview> {
    Logger.log("AnkiTasks: QueryAnkiSchedule: doInBackground entered")

    val reviewInfos = mutableListOf<AnkiCardForReview>()

    val scheduledCardsUri: Uri = FlashCardsContract.ReviewInfo.CONTENT_URI

    val deckSelector = when (deckId) {
        -1L -> "limit=?"
        else -> "limit=?, deckID=?, "
    }

    val deckArguments = when (deckId) {
        -1L -> arrayOf(limit.toString())
        else -> arrayOf(limit.toString(), deckId.toString())
    }

    contentResolver.query(scheduledCardsUri, null, deckSelector, deckArguments, null)
        ?.use {
            while (it.moveToNext()) {
                reviewInfos += AnkiCardForReview(
                    noteId = it.getLong(0),
                    cardOrd = it.getInt(1),
                    buttonCount = it.getInt(2),
                    nextReviewTimes = it.getString(3),
                    isMedia = it.getString(4) != EMPTY_MEDIA
                )
            }
        }

    return reviewInfos
}

class UpdateAnkiSchedule(val reviewedCard: AnkiCardReviewed, val onCompletionListener: OnCompletionListener) :
    AsyncTask<ContentResolver, Void,
        Int>() {

    interface OnCompletionListener {
        fun onComplete(numUpdated: Int)
    }

//    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): Int {
        val cr: ContentResolver = params[0] ?: return 0

        val scheduledCardsUri: Uri = FlashCardsContract.ReviewInfo.CONTENT_URI

        val values = ContentValues()
        with(values) {
            put(FlashCardsContract.ReviewInfo.NOTE_ID, reviewedCard.noteId)
            put(FlashCardsContract.ReviewInfo.CARD_ORD, reviewedCard.cardOrd)
            put(FlashCardsContract.ReviewInfo.EASE, reviewedCard.ease)
            put(FlashCardsContract.ReviewInfo.TIME_TAKEN, reviewedCard.timeTaken)
        }


        return cr.update(scheduledCardsUri, values, null, null)
    }

    override fun onPostExecute(result: Int) {
//        weakReferenceListener.get()?.onComplete(result ?: 0)
        onCompletionListener.onComplete(result)
    }
}

suspend fun updateAnkiSchedule(reviewedCard: AnkiCardReviewed, contentResolver: ContentResolver): Int {
    val scheduledCardsUri: Uri = FlashCardsContract.ReviewInfo.CONTENT_URI

    val values = ContentValues()
    with(values) {
        put(FlashCardsContract.ReviewInfo.NOTE_ID, reviewedCard.noteId)
        put(FlashCardsContract.ReviewInfo.CARD_ORD, reviewedCard.cardOrd)
        put(FlashCardsContract.ReviewInfo.EASE, reviewedCard.ease)
        put(FlashCardsContract.ReviewInfo.TIME_TAKEN, reviewedCard.timeTaken)
    }


    return contentResolver.update(scheduledCardsUri, values, null, null)
}

class QueryAnkiSimpleCards(private val reviewInfo: List<AnkiCardForReview>,
                           val onCompletionListener: OnCompletionListener) :
    AsyncTask<ContentResolver, Void, List<AnkiCardForReview>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<AnkiCardForReview>)
    }

//    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<AnkiCardForReview> {
        val cr: ContentResolver = params[0] ?: return emptyList()

        var noteUri: Uri
        var cardsUri: Uri
        var specifiCardUri: Uri

        val projection = FlashCardsContract.Card.DEFAULT_PROJECTION + arrayOf(FlashCardsContract.Card.QUESTION_SIMPLE,
            FlashCardsContract.Card.ANSWER_SIMPLE,
            FlashCardsContract.Card.ANSWER_PURE)

        reviewInfo.forEach { card ->
            noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, card.noteId.toString())
            cardsUri = Uri.withAppendedPath(noteUri, "cards")
            specifiCardUri = Uri.withAppendedPath(cardsUri, card.cardOrd.toString())
            cr.query(specifiCardUri,
                projection,
                null,
                null,
                null)
                ?.use {
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

    override fun onPostExecute(result: List<AnkiCardForReview>?) {
//        weakReferenceListener.get()?.onComplete(result ?: emptyList())
        onCompletionListener.onComplete(result ?: emptyList())
    }
}

class QueryAnkiSpecificSimpleCards(private val reviewInfo: List<AnkiCardForReview>, val onCompletionListener:
OnCompletionListener) :
    AsyncTask<ContentResolver, Void, List<AnkiCard>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<AnkiCard>)
    }

//    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<AnkiCard> {
        Logger.log("AnkiTasks: QueryAnkiSpecificSimpleCards: doInBackground entered")
        val cr: ContentResolver = params[0] ?: return emptyList()

        var noteUri: Uri
        var cardsUri: Uri
        var specifiCardUri: Uri

        val projection = FlashCardsContract.Card.DEFAULT_PROJECTION + arrayOf(FlashCardsContract.Card.QUESTION_SIMPLE,
            FlashCardsContract.Card.ANSWER_SIMPLE,
            FlashCardsContract.Card.ANSWER_PURE)
        val cards = mutableListOf<AnkiCard>()

        reviewInfo.forEach { review ->
            noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, review.noteId.toString())
            cardsUri = Uri.withAppendedPath(noteUri, "cards")
            specifiCardUri = Uri.withAppendedPath(cardsUri, review.cardOrd.toString())
            cr.query(specifiCardUri,
                projection,
                null,
                null,
                null)?.use {
                if (!it.moveToFirst()) return@use
                val noteId = it.getLong(0)
                val cardOrd = it.getInt(1)
                val cardName = it.getString(2)
                val did = it.getString(3)
                val question = it.getString(4)
                val answer = it.getString(5)
                val questionSimple = it.getString(6)
                val answerSimple = it.getString(7)
                val answerPure = it.getString(8)
                cards += AnkiCard(noteId, -1L, cardOrd, cardName, did, question, answer, questionSimple, answerSimple,
                    answerPure, review.isMedia, review.buttonCount)
            }
        }

        return cards
    }

    override fun onPostExecute(result: List<AnkiCard>?) {
        Logger.log("AnkiTasks: QueryAnkiSpecificSimpleCards: onPostExecute entered")
//        weakReferenceListener.get()?.onComplete(result ?: emptyList())
        onCompletionListener.onComplete(result ?: emptyList())
    }
}

suspend fun queryAnkiSpecificSimpleCards(reviewInfo: List<AnkiCardForReview>, contentResolver: ContentResolver): List<AnkiCard> {
    Logger.log("AnkiTasks: suspend queryAnkiSpecificSimpleCards entered")

    var noteUri: Uri
    var cardsUri: Uri
    var specificCardUri: Uri

    val projection = FlashCardsContract.Card.DEFAULT_PROJECTION +
        arrayOf(
            FlashCardsContract.Card.QUESTION_SIMPLE,
            FlashCardsContract.Card.ANSWER_SIMPLE,
            FlashCardsContract.Card.ANSWER_PURE
        )
    val cards = mutableListOf<AnkiCard>()

    reviewInfo.forEach { review ->
        noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, review.noteId.toString())
        cardsUri = Uri.withAppendedPath(noteUri, "cards")
        specificCardUri = Uri.withAppendedPath(cardsUri, review.cardOrd.toString())
        contentResolver.query(specificCardUri,
            projection,
            null,
            null,
            null
        )?.use {
            if (!it.moveToFirst()) return@use
            cards += AnkiCard(
                noteId = it.getLong(0),
                modelId = -1L,
                cardOrd = it.getInt(1),
                cardName = it.getString(2),
                did = it.getString(3),
                question = it.getString(4),
                answer = it.getString(5),
                questionSimple = it.getString(6),
                answerSimple = it.getString(7),
                answerPure = it.getString(8),
                media = review.isMedia,
                buttonCount = review.buttonCount
            )
        }
    }

    return cards
}


class QueryAnkiModels(private val cards: List<AnkiCard>, val onCompletionListener: OnCompletionListener) :
    AsyncTask<ContentResolver, Void, List<AnkiCard>>() {

    interface OnCompletionListener {
        fun onComplete(reviewInfo: List<AnkiCard>)
    }

//    private val weakReferenceListener: WeakReference<OnCompletionListener> = WeakReference(onCompletionListener)

    override fun doInBackground(vararg params: ContentResolver?): List<AnkiCard> {
        Logger.log("AnkiTasks: QueryAnkiModels: doInBackground entered")
        val cr: ContentResolver = params[0] ?: return emptyList()

        var noteUri: Uri

        val projection = arrayOf(FlashCardsContract.Note.MID)

        cards.forEach { card ->
            noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, card.noteId.toString())
            cr.query(noteUri,
                projection,
                null,
                null,
                null)?.use {
                if (!it.moveToFirst()) return@use
                card.modelId = it.getLong(0)
            }
        }

        return cards
    }

    override fun onPostExecute(result: List<AnkiCard>?) {
        Logger.log("AnkiTasks: QueryAnkiModels: onPostExecute entered")
//        weakReferenceListener.get()?.onComplete(result ?: emptyList())
        onCompletionListener.onComplete(result ?: emptyList())
    }
}

suspend fun queryAnkiModels(cards: List<AnkiCard>, contentResolver: ContentResolver): List<AnkiCard> {
    Logger.log("AnkiTasks: suspend queryAnkiModels entered")

    var noteUri: Uri
    val projection = arrayOf(FlashCardsContract.Note.MID)

    cards.forEach { card ->
        noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, card.noteId.toString())
        contentResolver.query(
            noteUri,
            projection,
            null,
            null,
            null
        )?.use {
            if (!it.moveToFirst()) return@use
            card.modelId = it.getLong(0)
        }
    }

    return cards
}