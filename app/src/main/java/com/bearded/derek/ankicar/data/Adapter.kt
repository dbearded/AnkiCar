package com.bearded.derek.ankicar.data

import com.bearded.derek.ankicar.model.DbCard
import com.bearded.derek.ankicar.model.Repository
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard
import com.bearded.derek.ankicar.model.anki.Card
import com.bearded.derek.ankicar.model.anki.UNHANDLED
import com.bearded.derek.ankicar.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ReviewAdapter(
    private val callback: Callback,
    private val repo: Repository,
    var cacheLimit: Int = 3
) {
    interface Callback {
        fun reviewComplete()
        fun nextCard(card: Card)
    }

    private var readyForInput = true

    var deckId: Long = -1L

    private lateinit var currentCard: Card
    private var cardStartTime = -1L
    private var flagged = true

    private var unhandled = listOf<Card>()
    private val skipped = mutableListOf<Card>()
    private val reviewed = mutableListOf<ReviewAction>()
    private var forReview = mutableListOf<Card>()

    private lateinit var reviewQueue: List<Card>

    fun init(deckId: Long?) {
        log("Adapter: Init entered - ready for input: $readyForInput")
        if (readyForInput) {
            readyForInput = false
            reset()
            this.deckId = deckId ?: -1L
            GlobalScope.launch {
                fetchCards()
            }
        }
    }

    fun answer(ease: Int) {
        log("Adapter: answer entered - ready for input: $readyForInput - current card id: ${currentCard.noteId}")
        if (readyForInput) {
            readyForInput = false
            val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, 60 * 1000L)
            record(ReviewAction.ReviewResult(currentCard, ease, timeTaken, flagged))
            advance()
        }
    }

    fun skip() {
        log("Adapter: skip entered - ready for input: $readyForInput - current card id: ${currentCard.noteId}")
        if (readyForInput) {
            readyForInput = false
            record(ReviewAction.SkipResult(currentCard, flagged))
            advance()
        }
    }

    fun previous() {
        if (readyForInput) {
            log("Adapter: previous entered - ready for input: $readyForInput")
            if (reviewed.isNotEmpty()) {
                currentCard = reviewed.removeAt(reviewed.size - 1).card
                sendNext()
            }
        }
    }

    fun flag() {
        flagged = !flagged
    }

    fun done() {
        GlobalScope.launch {
            flush()
            reset()
        }
    }

    private suspend fun flush() {
        log("Adapter: flush entered - reviewed size: ${reviewed.size}")
        cacheLimit = 0
        withContext(Dispatchers.Default) {
            while (reviewed.size > 0) {
                handleOverflow()
            }
        }
    }

    private fun record(reviewAction: ReviewAction) {
        log("Adapter: record entered - reviewAction CardId: ${reviewAction.card.noteId}")
        reviewed += reviewAction
        handleOverflow()
    }

    private fun handleOverflow() {
        log("Adapter: handleOverflow entered - cache overflow amount: ${reviewed.size - cacheLimit}")
        if (reviewed.size > cacheLimit) {
            val last = reviewed.removeAt(0)
            when (last) {
                is ReviewAction.SkipResult -> persistSkip(last)
                is ReviewAction.ReviewResult -> persistReview(last)
            }
        }
    }

    private fun persistSkip(skip: ReviewAction.SkipResult) {
        log("Adapter: persistSkip entered - cardId: ${skip.card.noteId}")
        GlobalScope.launch {
            repo.insertCardInDb(DbCard(skip.card.noteId, skip.card.cardOrd, skip.card.buttonCount,
                skip.card.question, skip.card.answer, skip.flagged, -1, -1, Date(System
                .currentTimeMillis())))

            log("Adapter: persistSkip TransactionListener onComplete entered - cardId: ${skip.card.noteId}")
            skipped += skip.card
        }
    }

    private fun persistReview(review: ReviewAction.ReviewResult) {
        log("Adapter: persistReview entered - cardId: ${review.card.noteId}")
        GlobalScope.launch {
            repo.insertCardInDb(DbCard(review.card.noteId, review.card.cardOrd, review.card.buttonCount,
                review.card.question, review.card.answer, review.flagged, review.ease, review.timeTaken, Date(System
                .currentTimeMillis())))

            log("Adapter: sendReviewToAnki entered - cardId: ${review.card.noteId}")
            val reviewedCard = AnkiReviewCard.AnkiCardReviewed(review.card.noteId, review.card.cardOrd,
                review.toAnkiEase(), System.currentTimeMillis() - review.timeTaken)

            repo.sendReviewToAnki(reviewedCard)
        }
    }

    private fun ReviewAction.ReviewResult.toAnkiEase(): Int {
        log("Adapter: toAnkiEase entered - cardId: ${this.card.noteId} - cardButtonCount: ${this.card.buttonCount}")
        return when {
            this.card.buttonCount == 3 && this.ease > 1 -> this.ease - 1
            else -> this.ease
        }
    }

    private suspend fun fetchCards() {
        do {
            val limit = reviewed.size + skipped.size + unhandled.size + 1
            reviewQueue = repo.queryForCards(deckId, limit)
            log("Adapter: onQueryComplete entered - card List size: ${reviewQueue.size}")
            unhandled = reviewQueue.filter { it.question == UNHANDLED }
            log("Adapter: onQueryComplete unhandled cards size: ${unhandled.size}")
            forReview = reviewQueue.asSequence().minus(getCacheCards()).minus(skipped).minus(unhandled).toMutableList()
            log("Adapter: onQueryComplete distinct cards size: ${forReview.size}")
        } while (forReview.isEmpty() && limit == reviewQueue.size)

        if (forReview.isEmpty()) {
            done()
            withContext(Dispatchers.Main) {
                callback.reviewComplete()
            }
        } else {
            currentCard = forReview.removeAt(0)
            sendNext()
        }
    }

    private fun advance() {
        if (forReview.isNotEmpty()) {
            currentCard = forReview.removeAt(0)
            sendNext()
            return
        }

        GlobalScope.launch {
            fetchCards()
        }
    }

    private fun sendNext() {
        log("Adapter: sendNext entered - cardId: ${currentCard.noteId}")
        cardStartTime = System.currentTimeMillis()
        readyForInput = true
        flagged = false
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                callback.nextCard(currentCard)
            }
        }
    }

    private fun getCacheCards(): List<Card> {
        log("Adapter: getCacheCards entered - reviewed size: ${reviewed.size}")
        return reviewed.map {
            when (it) {
                is ReviewAction.ReviewResult -> it.card
                is ReviewAction.SkipResult -> it.card
            }
        }
    }

    private fun reset() {
        log("Adapter: Resetting")
        reviewed.clear()
        skipped.clear()
        reviewQueue = emptyList()
        readyForInput = true
    }

    private sealed class ReviewAction() {
        class ReviewResult(val card: Card, val ease: Int, val timeTaken: Long, var flagged: Boolean = false) :
            ReviewAction()
        class SkipResult(val card: Card, var flagged: Boolean = false) : ReviewAction()
    }

    private val ReviewAction.card: Card
        get() = when (this) {
            is ReviewAction.ReviewResult -> card
            is ReviewAction.SkipResult -> card
        }
}