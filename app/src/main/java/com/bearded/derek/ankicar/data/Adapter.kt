package com.bearded.derek.ankicar.data

import android.content.ContentResolver
import android.text.TextUtils
import com.bearded.derek.ankicar.*
import com.bearded.derek.ankicar.model.*
import java.util.*

class ReviewAdapter(private val callback: Callback, private val contentResolver: ContentResolver,
                    private val ankiDatabase: AnkiDatabase) {

    interface Callback {
        fun reviewComplete()
        fun nextCard(card: Card)
    }

    var deckId: Long = -1L
        set(value) {
            reset()
            field = value
        }

    private val ankiTaskCompletionListener = object : CardCompletionListener {
        override fun onQueryComplete(cards: List<Card>) {
            requestInFlight = false
            reviewQueue = cards
            val prevLimit = postReviewCache.size + skipList.size + unhandledCards.size +  1
            cards.filterTo(unhandledCards) {
                TextUtils.equals(it.question, UNHANDLED)
            }
            val querySize = reviewQueue.size
            // Only unhandled cards and only if the query keeps returning new cards
            if (unhandledCards.size == reviewQueue.size && prevLimit == querySize) {
                queryForCards()
            } else {
                next()
            }
        }

        override fun onUpdateComplete(numUpdated: Int) {
            requestInFlight = false
            queryForCards()
        }
    }

    private fun manageUnhandledCards(cards: List<Card>) {

    }

    var cacheLimit = 3
    private  var requestInFlight = false
    private var readyForInput = true
    private lateinit var currentCard: Card
    private var cardStartTime = -1L
    private val postReviewCache = mutableListOf<ReviewAction>()
    private val skipList = mutableListOf<Card>()
    private val unhandledCards = mutableSetOf<Card>()
    private var flagged = false

    private lateinit var reviewQueue: List<Card>

    fun init(deckId: Long?) {
        if (readyForInput) {
            readyForInput = false
            reset()
            this.deckId = deckId ?: -1L
            queryForCards()
        }
    }

    fun answer(ease: Int) {
        if (readyForInput) {
            readyForInput = false
            val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, 60*1000L)
            addToCache(ReviewAction.ReviewResult(currentCard, ease, timeTaken, flagged))
            queryForCards()
        }
    }

    fun skip() {
        if (readyForInput) {
            readyForInput = false
            addToCache(ReviewAction.SkipResult(currentCard, flagged))
            queryForCards()
        }
    }

    fun previous() {
        if (postReviewCache.isNotEmpty()) {
            currentCard = postReviewCache.removeAt(postReviewCache.size - 1).getCard()
            sendNext(currentCard)
        }
    }

    fun flag() {
        flagged = !flagged
    }

    fun getSkips(): List<Card> {
        return skipList.toList()
    }

    private fun addToCache(reviewAction: ReviewAction) {
        postReviewCache += reviewAction
        handleOverflow()
    }

    private fun handleOverflow() {
        if (postReviewCache.size > cacheLimit) {
            val last = postReviewCache.removeAt(0)
            when (last) {
                is ReviewAction.SkipResult -> persistSkip(last)
                is ReviewAction.ReviewResult -> persistReview(last)
            }
        }
    }

    private fun persistSkip(skip: ReviewAction.SkipResult) {
        requestInFlight = true
        val listener = object : TransactionListener {
            override fun onComplete() {
                skipList += skip.card
            }
        }
        insertCard(ankiDatabase, listener, DbCard(skip.card.noteId, skip.card.cardOrd, skip.card.buttonCount,
                skip.card.question, skip.card.answer, skip.flagged, -1, -1, Date(System
                .currentTimeMillis())))
    }

    private fun persistReview(review: ReviewAction.ReviewResult) {
        requestInFlight = true
        val listener = object : TransactionListener {
            override fun onComplete() {
                sendReviewToAnki(review)
            }
        }
        
        insertCard(ankiDatabase, listener, DbCard(review.card.noteId, review.card.cardOrd, review.card.buttonCount,
                review.card.question, review.card.answer, review.flagged, review.ease, review.timeTaken, Date(System
                .currentTimeMillis())))
    }

    private fun sendReviewToAnki(review: ReviewAction.ReviewResult) {
        requestInFlight = true
        val reviewedCard = AnkiReviewCard.AnkiCardReviewed(review.card.noteId, review.card.cardOrd,
                mapToAnkiEase(review), System.currentTimeMillis() - review.timeTaken)

        updateAnki(reviewedCard, contentResolver, ankiTaskCompletionListener)
    }

    private fun mapToAnkiEase(review: ReviewAction.ReviewResult): Int {
        if (review.card.buttonCount == 3) {
            if (review.ease > 1) {
                return review.ease - 1
            }
        }

        return review.ease
    }

    private fun queryForCards() {
        if (!requestInFlight) {
            requestInFlight = true
            val limit = postReviewCache.size + skipList.size + unhandledCards.size +  1
            queryReviewCards(deckId, limit, contentResolver, ankiTaskCompletionListener)
        }
    }

    private fun next() {
        val distinct = reviewQueue.minus(getCacheCards()).minus(skipList).minus(unhandledCards)
        if (distinct.isNotEmpty()) {
            currentCard = distinct.first()
            sendNext(currentCard)
        } else {
            if (postReviewCache.isEmpty()) {
                reviewComplete()
            } else {
                cacheLimit--
                handleOverflow()
                if (!requestInFlight) {
                    next()
                }
            }
        }
    }

    private fun sendNext(card: Card) {
        cardStartTime = System.currentTimeMillis();
        readyForInput = true
        flagged = false
        callback.nextCard(card)
    }

    private fun reviewComplete() {
        readyForInput = false
        postReviewCache.clear()
        reviewQueue = emptyList()
        callback.reviewComplete()
    }

    private fun getCacheCards(): List<Card> {
        return postReviewCache.map {
            when(it) {
                is ReviewAction.ReviewResult -> it.card
                is ReviewAction.SkipResult -> it.card
            }
        }
    }

    private fun reset() {
        postReviewCache.clear()
        skipList.clear()
        reviewQueue = emptyList()
    }

    private sealed class ReviewAction {
        class ReviewResult(val card: Card, val ease: Int, val timeTaken: Long, var flagged: Boolean = false):
                ReviewAction()
        class SkipResult(val card: Card, var flagged: Boolean = false): ReviewAction()
    }

    private fun ReviewAction.getCard(): Card {
        return when (this) {
            is ReviewAction.ReviewResult -> card
            is ReviewAction.SkipResult -> card
        }
    }
}