package com.bearded.derek.ankicar.data

import android.content.ContentResolver
import android.text.TextUtils
import android.util.Log
import com.bearded.derek.ankicar.model.*
import com.bearded.derek.ankicar.model.anki.*
import com.bearded.derek.ankicar.utils.Logger
import java.util.*

class ReviewAdapter(private val callback: Callback, private val contentResolver: ContentResolver,
                    private val ankiDatabase: AnkiDatabase) {

    interface Callback {
        fun reviewComplete()
        fun nextCard(card: Card)
    }

    var deckId: Long = -1L

    private val ankiTaskCompletionListener = object : CardCompletionListener {
        override fun onQueryComplete(cards: List<Card>) {
            Logger.log("Adapter: onQueryComplete entered - card List size: ${cards.size}")
            requestInFlight = false
            reviewQueue = cards
            val prevLimit = postReviewCache.size + skipList.size + unhandledCards.size +  1
            unhandledCards = cards.filter {
                TextUtils.equals(it.question, UNHANDLED)
            }
            Logger.log("Adapter: onQueryComplete unhandled cards size: ${unhandledCards.size}")
            val querySize = reviewQueue.size
            // Only unhandled cards and only if the query keeps returning new cards
            if (unhandledCards.size == reviewQueue.size && prevLimit == querySize) {
                queryForCards()
            } else {
                next()
            }
        }

        override fun onUpdateComplete(numUpdated: Int) {
            Logger.log("Adapter: onUpdateComplete entered - updated count: $numUpdated")
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
    private var unhandledCards = listOf<Card>()
    private var flagged = false

    private lateinit var reviewQueue: List<Card>

    fun init(deckId: Long?) {
        Logger.log("Adapter: Init entered - ready for input: $readyForInput")
        if (readyForInput) {
            readyForInput = false
            reset()
            this.deckId = deckId ?: -1L
            queryForCards()
        }
    }

    fun answer(ease: Int) {
        Logger.log("Adapter: answer entered - ready for input: $readyForInput - current card id: ${currentCard.noteId}")
        if (readyForInput) {
            readyForInput = false
            val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, 60*1000L)
            addToCache(ReviewAction.ReviewResult(currentCard, ease, timeTaken, flagged))
            queryForCards()
        }
    }

    fun skip() {
        Logger.log("Adapter: skip entered - ready for input: $readyForInput - current card id: ${currentCard.noteId}")
        if (readyForInput) {
            readyForInput = false
            addToCache(ReviewAction.SkipResult(currentCard, flagged))
            queryForCards()
        }
    }

    fun previous() {
        // TODO this may be causing an issue because it's mutating the postReviewCache while something else could
        // potentially be happening in the background
        Logger.log("Adapter: previous entered - ready for input: $readyForInput")
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

    fun flush() {
        Logger.log("Adapter: flush entered - postReviewCache size: ${postReviewCache.size}")
        cacheLimit = 0
        while (postReviewCache.size > 0) {
            handleOverflow()
        }
    }

    private fun addToCache(reviewAction: ReviewAction) {
        Logger.log("Adapter: addToCache entered - reviewAction CardId: ${reviewAction.getCard().noteId}")
        postReviewCache += reviewAction
        handleOverflow()
    }

    private fun handleOverflow() {
        Logger.log("Adapter: handleOverflow entered - cache overflow amount: ${postReviewCache.size - cacheLimit}")
        if (postReviewCache.size > cacheLimit) {
            val last = postReviewCache.removeAt(0)
            when (last) {
                is ReviewAction.SkipResult -> persistSkip(last)
                is ReviewAction.ReviewResult -> persistReview(last)
            }
        }
    }

    private fun persistSkip(skip: ReviewAction.SkipResult) {
        Logger.log("Adapter: persistSkip entered - cardId: ${skip.card.noteId}")
        requestInFlight = true
        val listener = object : TransactionListener {
            override fun onComplete() {
                Logger.log("Adapter: persistSkip TransactionListener onComplete entered - cardId: ${skip.card.noteId}")
                skipList += skip.card
            }
        }

        insertCard(ankiDatabase, listener, DbCard(skip.card.noteId, skip.card.cardOrd, skip.card.buttonCount,
                skip.card.question, skip.card.answer, skip.flagged, -1, -1, Date(System
                .currentTimeMillis())))
    }

    private fun persistReview(review: ReviewAction.ReviewResult) {
        Logger.log("Adapter: persistReview entered - cardId: ${review.card.noteId}")
        requestInFlight = true
        val listener = object : TransactionListener {
            override fun onComplete() {
                Logger.log("Adapter: persistReview TransactionListener onComplete entered - cardId: ${review.card.noteId}")
                sendReviewToAnki(review)
            }
        }
        
        insertCard(ankiDatabase, listener, DbCard(review.card.noteId, review.card.cardOrd, review.card.buttonCount,
                review.card.question, review.card.answer, review.flagged, review.ease, review.timeTaken, Date(System
                .currentTimeMillis())))
    }

    private fun sendReviewToAnki(review: ReviewAction.ReviewResult) {
        Logger.log("Adapter: sendReviewToAnki entered - cardId: ${review.card.noteId}")
        requestInFlight = true
        val reviewedCard = AnkiReviewCard.AnkiCardReviewed(review.card.noteId, review.card.cardOrd,
                mapToAnkiEase(review), System.currentTimeMillis() - review.timeTaken)

        updateAnki(reviewedCard, contentResolver, ankiTaskCompletionListener)
    }

    private fun mapToAnkiEase(review: ReviewAction.ReviewResult): Int {
        Logger.log("Adapter: mapToAnkiEase entered - cardId: ${review.card.noteId} - cardButtonCount: ${review
                .card.buttonCount}")
        if (review.card.buttonCount == 3) {
            if (review.ease > 1) {
                return review.ease - 1
            }
        }

        return review.ease
    }

    private fun queryForCards() {
        Logger.log("Adapter: queryForCards entered - requestInFlight: $requestInFlight")
        if (!requestInFlight) {
            requestInFlight = true
            val limit = postReviewCache.size + skipList.size + unhandledCards.size +  1
            queryReviewCards(deckId, limit, contentResolver, ankiTaskCompletionListener)
        }
    }

    private fun next() {
        Logger.log("Adapter: next entered")
        val distinct = reviewQueue.minus(getCacheCards()).minus(skipList).minus(unhandledCards)
        if (distinct.isNotEmpty()) {
            Logger.log("Adapter: next - distinct size: ${distinct.size}")
            currentCard = distinct.first()
            sendNext(currentCard)
        } else {
            if (postReviewCache.isEmpty()) {
                Logger.log("Adapter: next - postReviewCache empty")
                reviewComplete()
            } else {
                cacheLimit--
                Logger.log("Adapter: next - cacheLimit: $cacheLimit")
                handleOverflow()
                if (!requestInFlight) {
                    next()
                }
            }
        }
    }

    private fun sendNext(card: Card) {
        Logger.log("Adapter: sendNext entered - cardId: ${card.noteId}")
        cardStartTime = System.currentTimeMillis();
        readyForInput = true
        flagged = false
        callback.nextCard(card)
    }

    private fun reviewComplete() {
        Logger.log("Adapter: reviewComplete entered - postReviewCache size: ${postReviewCache.size}")
        readyForInput = false
        postReviewCache.clear()
        reviewQueue = emptyList()
        callback.reviewComplete()
    }

    private fun getCacheCards(): List<Card> {
        Logger.log("Adapter: getCacheCards entered - postReviewCache size: ${postReviewCache.size}")
        return postReviewCache.map {
            when(it) {
                is ReviewAction.ReviewResult -> it.card
                is ReviewAction.SkipResult -> it.card
            }
        }
    }

    private fun reset() {
        Logger.log("Adapter: Resetting")
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