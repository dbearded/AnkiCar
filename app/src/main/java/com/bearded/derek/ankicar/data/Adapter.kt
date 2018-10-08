package com.bearded.derek.ankicar.data

import android.content.ContentResolver
import com.bearded.derek.ankicar.AnkiReviewCard
import com.bearded.derek.ankicar.Card
import com.bearded.derek.ankicar.CardCompletionListener
import com.bearded.derek.ankicar.queryReviewCards

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

    companion object {

        const val CACHE_SIZE = 3
    }

    private val ankiTaskCompletionListener = object : CardCompletionListener {
        override fun onQueryComplete(cards: List<Card>) {
            reviewQueue = cards

            next()
        }

        override fun onUpdateComplete() {
            queryForCards()
        }
    }

    private var isInitialized = false
    private var skipsInCache = 0
    private lateinit var currentCard: Card
    private var cardStartTime = -1L
    private val postReviewCache = mutableListOf<ReviewAction>()
    private val skipList = mutableListOf<Card>()

    private lateinit var reviewQueue: List<Card>

    fun init(deckId: Long) {
        this.deckId = deckId
        queryForCards()
    }

    fun answer(ease: Int) {
        if (isInitialized) {
            val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, 60*1000L)
            addToCache(ReviewAction.ReviewResult(currentCard, ease, timeTaken))
//            next()
            queryForCards()
        }
    }

    fun skip() {
        if (isInitialized) {
            addToCache(ReviewAction.SkipResult(currentCard))
//            next()
            queryForCards()
        }
    }

    fun previous() {
        if (isInitialized) {
            if (postReviewCache.isNotEmpty()) {
                sendNext(getPreviousFromCache().getCard())
            }
        }
    }

    fun flag() {
        if (isInitialized) {

        }
    }

    fun getSkips(): List<Card> {
        return skipList.toList()
    }

    private fun addToCache(reviewAction: ReviewAction) {
        postReviewCache += reviewAction
        if (reviewAction is ReviewAction.SkipResult) {
            skipsInCache++
        }
    }

    private fun getPreviousFromCache(): ReviewAction {
        val prev = postReviewCache.removeAt(postReviewCache.size - 1)
        if (prev is ReviewAction.SkipResult) {
            skipsInCache--
        }
        return prev
    }

    private fun stashSkip(reviewSkip: ReviewAction.SkipResult) {
        skipsInCache--
        skipList += reviewSkip.card
    }

    private fun sendReviewToAnki(review: ReviewAction.ReviewResult) {
        val reviewedCard = AnkiReviewCard.AnkiCardReviewed(review.card.noteId, review.card.cardOrd,
                review.ease.toString(), review.timeTaken.toString())

        // TODO send update to Anki
        ankiTaskCompletionListener.onUpdateComplete()
    }

    private fun queryForCards() {
        val limit = postReviewCache.size + skipList.size + 1
        queryReviewCards(deckId, 100, contentResolver, ankiTaskCompletionListener)
    }

//    private fun getCurrentCard(): Card {
//        // TODO Is this the right list?
//        val card = postReviewCache[0]
//        return when(card) {
//            is ReviewAction.ReviewResult -> card.card
//            is ReviewAction.SkipResult -> card.card
//        }
//    }

    private fun sendNext(card: Card) {
        isInitialized = true
        cardStartTime = System.currentTimeMillis();
        callback.nextCard(card)
    }

    private fun getCacheCards(): List<Card> {
        return postReviewCache.map {
            when(it) {
                is ReviewAction.ReviewResult -> it.card
                is ReviewAction.SkipResult -> it.card
            }
        }
    }

    private fun next() {
        if (reviewQueue.isEmpty()) {
            callback.reviewComplete()
        }

        if (postReviewCache.size > CACHE_SIZE) {
            val reviewAction = postReviewCache.removeAt(0)
            when (reviewAction) {
                is ReviewAction.SkipResult -> stashSkip(reviewAction)
                is ReviewAction.ReviewResult -> {
                    sendReviewToAnki(reviewAction)
                    return
                }
            }
        }
        val cacheCards = getCacheCards()
        val distinct = reviewQueue.minus(getCacheCards()).minus(skipList)
        if (distinct.isNotEmpty()) {
            currentCard = distinct.first()
            sendNext(currentCard)
        } else {
            if (skipsInCache + skipList.size == reviewQueue.size) {
                callback.reviewComplete()
            } else {
                var shouldUpdate = false
                var last: ReviewAction
                while (postReviewCache.isNotEmpty()) {
                    last = postReviewCache[0]
                    when (last) {
                        is ReviewAction.SkipResult -> {
                            stashSkip(last)
                            postReviewCache.remove(last)
                        }
                        is ReviewAction.ReviewResult -> shouldUpdate = true
                    }
                    if (shouldUpdate) {
                        break
                    }
                }
                if (shouldUpdate) {
                    sendReviewToAnki(postReviewCache.removeAt(0)
                            as ReviewAction.ReviewResult)
                }
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