package com.bearded.derek.ankicar.model.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard.AnkiCardForReview
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard.AnkiCardReviewed
import com.bearded.derek.ankicar.model.anki.Card.Companion.build
import com.bearded.derek.ankicar.utils.Logger
import com.bearded.derek.ankicar.utils.log
import com.ichi2.anki.FlashCardsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CardCompletionListener {
    fun onQueryComplete(cards: List<Card>)
    fun onUpdateComplete(numUpdated: Int)
}

const val EMPTY_MEDIA: String = "[]"

suspend fun queryReviewCards(deckId: Long, limit: Int, contentResolver: ContentResolver): List<Card> {
    log("AnkiTasks: queryForReviewCards2 entered - limit: $limit")
    val reviewInfo = queryAnkiSchedule(deckId, limit, contentResolver)

    log("AnkiTasks: queryAnkiSchedule - ankiCardForReview size: ${reviewInfo.size}")
    val ankiCards = queryAnkiSpecificSimpleCards(reviewInfo, contentResolver)

    log("AnkiTasks: queryAnkiSpecificSimpleCards entered - ankiCard size: ${ankiCards.size}")
    queryAnkiModels(ankiCards, contentResolver)

    return ankiCards.map {
        it.build(it.getCleanser())
    }
}

suspend fun updateAnki(reviewedCard: AnkiCardReviewed, contentResolver: ContentResolver): Int {
    log("AnkiTasks: updateAnki entered - reviewedCard: ${reviewedCard.noteId}")
    return updateAnkiSchedule(reviewedCard, contentResolver)
}

suspend fun queryAnkiSchedule(deckId: Long, limit: Int, contentResolver: ContentResolver): List<AnkiCardForReview> {
    log("AnkiTasks: QueryAnkiSchedule: doInBackground entered")

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

    withContext(Dispatchers.IO) {
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
    }

    return reviewInfos
}

fun updateAnkiSchedule(reviewedCard: AnkiCardReviewed, contentResolver: ContentResolver): Int {
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

suspend fun queryAnkiSimpleCards(reviewInfo: List<AnkiCardForReview>, contentResolver: ContentResolver): List<AnkiCardForReview> {
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
        contentResolver.query(specifiCardUri,
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

suspend fun queryAnkiSpecificSimpleCards(reviewInfo: List<AnkiCardForReview>, contentResolver: ContentResolver): List<AnkiCard> {
    log("AnkiTasks: suspend queryAnkiSpecificSimpleCards entered")

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

suspend fun queryAnkiModels(cards: List<AnkiCard>, contentResolver: ContentResolver): List<AnkiCard> {
    log("AnkiTasks: suspend queryAnkiModels entered")

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