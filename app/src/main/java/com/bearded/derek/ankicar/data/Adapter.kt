package com.bearded.derek.ankicar.data

import android.content.ContentResolver
import android.text.TextUtils
import android.view.TextureView
import com.bearded.derek.ankicar.*

class ReviewAdapter(private val callback: Callback, private val contentResolver: ContentResolver) {

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

    private lateinit var reviewQueue: List<Card>

    fun init(deckId: Long?) {
        if (readyForInput) {
            readyForInput = false
            this.deckId = deckId ?: -1L
            queryForCards()
        }
    }

    fun answer(ease: Int) {
        if (readyForInput) {
            readyForInput = false
            val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, 60*1000L)
            addToCache(ReviewAction.ReviewResult(currentCard, ease, timeTaken))
            queryForCards()
        }
    }

    fun skip() {
        if (readyForInput) {
            readyForInput = false
            addToCache(ReviewAction.SkipResult(currentCard))
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
        if (readyForInput) {

        }
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
                is ReviewAction.SkipResult -> skipList += last.card
                is ReviewAction.ReviewResult -> sendReviewToAnki(last)
            }
        }
    }

    private fun sendReviewToAnki(review: ReviewAction.ReviewResult) {
        requestInFlight = true
        val reviewedCard = AnkiReviewCard.AnkiCardReviewed(review.card.noteId, review.card.cardOrd,
                mapToAnkiEase(review), System.currentTimeMillis() - review.timeTaken)

        updateAnki(reviewedCard, contentResolver, ankiTaskCompletionListener)
        // TODO send update to Anki
//        ankiTaskCompletionListener.onUpdateComplete()
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
        class ReviewResult(val card: Card, val ease: Int, val timeTaken: Long): ReviewAction()
        class SkipResult(val card: Card): ReviewAction()
    }

    private fun ReviewAction.getCard(): Card {
        return when (this) {
            is ReviewAction.ReviewResult -> card
            is ReviewAction.SkipResult -> card
        }
    }
}