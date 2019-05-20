package com.bearded.derek.ankicar.data

import com.bearded.derek.ankicar.model.DbCard
import com.bearded.derek.ankicar.model.Repository
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard
import com.bearded.derek.ankicar.model.anki.Card
import com.bearded.derek.ankicar.model.anki.UNHANDLED
import com.bearded.derek.ankicar.utils.log
import kotlinx.coroutines.*
import java.util.*

class ReviewAdapter(
    private val callback: Callback,
    private val provider: CardProvider
) {
    interface Callback {
        fun reviewComplete()
        fun nextCard(card: Card)
    }

    private var readyForInput = true
    private lateinit var currentCard: Card
    private val trackers = mutableListOf<ReviewTracker>()

    fun start() {
        log("Adapter: Start entered - ready for input: $readyForInput")
        if (readyForInput) {
            readyForInput = false
            advance()
        }
    }

    private fun advance() {
        GlobalScope.launch {
            val card = provider.next()
            if (card == null) {
                withContext(Dispatchers.Main) {
                    callback.reviewComplete()
                }
                done()
            } else {
                currentCard = card
                sendNext()
            }
        }
    }

    fun answer(ease: Int) {
        log("Adapter: answer entered - ready for input: $readyForInput - current card id: ${currentCard.cardOrd}")
        if (readyForInput) {
            readyForInput = false
            provider.answer(ease)
            trackers.notify { onAnswer(currentCard, ease) }
            advance()
        }
    }

    fun skip() {
        log("Adapter: skip entered - ready for input: $readyForInput - current card id: ${currentCard.cardOrd}")
        if (readyForInput) {
            readyForInput = false
            provider.skip()
            trackers.notify { onSkip(currentCard) }
            advance()
        }
    }

    fun previous() {
        log("Adapter: previous entered - ready for input: $readyForInput")
        if (readyForInput) {
            GlobalScope.launch {
                provider.previous()?.let {
                    currentCard = it
                    sendNext()
                    trackers.notify { onPrevious() }
                }
            }
        }
    }

    fun flag() {
        if (readyForInput) {
            trackers.notify { onFlag(currentCard) }
        }
    }

    fun register(tracker: ReviewTracker) {
        trackers += tracker
    }

    fun unregister(tracker: ReviewTracker) {
        trackers -= tracker
    }

    fun done() {
        readyForInput = true
        provider.complete()
        trackers.notify { onComplete() }
    }

    private fun sendNext() {
        log("Adapter: sendNext entered - cardId: ${currentCard.noteId}")
        readyForInput = true
        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                callback.nextCard(currentCard)
            }
            trackers.notify { onNext(currentCard) }
        }
    }

    private fun List<ReviewTracker>.notify(block: ReviewTracker.() -> Unit) = this.forEach { it.block() }
}

interface CardProvider {
    suspend fun next(): Card?
    suspend fun previous(): Card?
    fun answer(ease: Int)
    fun skip()
    fun complete()
}

abstract class ReviewTracker {
    open var onAnswer: (Card, Int) -> Unit = { _, _ -> Unit }
    open var onSkip: (Card) -> Unit = { _ -> Unit }
    open var onFlag: (Card) -> Unit = { _ -> Unit }
    open var onPrevious: () -> Unit = { Unit }
    open var onNext: (Card) -> Unit = { _ -> Unit }
    open var onComplete: () -> Unit = { Unit }
}
class DefaultCardProvider(
    private val repo: Repository,
    private val cacheSize: Int = 3,
    deckId: Long? = -1L
) : CardProvider {

    companion object {
        private const val MAX_TIME: Long = 60_000L
    }

    var deckId: Long = deckId ?: -1L
        set(value) {
            field = value ?: -1L
            reset()
        }

    private var cardStartTime: Long = -1L
    private lateinit var currentCard: Card

    private val skipped = mutableListOf<Card>()
    private var unhandled = listOf<Card>()
    private var forReview = mutableListOf<Card>()
    private val reviewed = Cache<ReviewAction> {
        log("Provider: onOverflow entered")
        when (it) {
            is ReviewAction.Skip -> skipped += it.card
            is ReviewAction.Review -> GlobalScope.launch { repo.sendReviewToAnki(it.toAnkiCard()) }
        }
    }

    override suspend fun next(): Card? {
        log("Provider: next entered - forReview size: ${forReview.size}")
        if (forReview.isEmpty()) fetchCards()
        cardStartTime = System.currentTimeMillis()
        return if (forReview.isEmpty()) {
            reviewed.flush()
            null
        } else {
            currentCard = forReview.removeAt(0)
            currentCard
        }
    }

    override suspend fun previous(): Card? {
        val prev = reviewed.previous()?.card
        if (prev != null) {
            forReview.add(0, currentCard)
            currentCard = prev
        }
        return prev
    }

    override fun answer(ease: Int) {
        val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, MAX_TIME)
        reviewed.add(ReviewAction.Review(currentCard, ease, timeTaken))
    }

    override fun skip() { reviewed.add(ReviewAction.Skip(currentCard)) }

    override fun complete() { reviewed.flush() }

    private suspend fun fetchCards() {
        log("Provider: fetchCards entered")
        do {
            val limit = reviewed.size + skipped.size + unhandled.size + 1
            val reviewQueue = repo.queryForCards(deckId, limit)
            log("Provider: fetchCards reviewQueue size: ${reviewQueue.size}")
            unhandled = reviewQueue.filter { it.question == UNHANDLED }
            log("Provider: fetchCards unhandled cards size: ${unhandled.size}")
            forReview = reviewQueue.asSequence()
                .minus(reviewed.asList().map { it.card })
                .minus(skipped)
                .minus(unhandled)
                .toMutableList()
            log("Provider: fetchCards forReview cards size: ${forReview.size}")
        } while (forReview.isEmpty() && limit == reviewQueue.size)
    }

    private fun reset() {
        reviewed.clear()
        skipped.clear()
    }

    private sealed class ReviewAction(val card: Card) {
        class Review(card: Card, val ease: Int, val timeTaken: Long) : ReviewAction(card)
        class Skip(card: Card) : ReviewAction(card)
    }

    private fun ReviewAction.Review.toAnkiCard(): AnkiReviewCard.AnkiCardReviewed {
        return AnkiReviewCard.AnkiCardReviewed(this.card.noteId, this.card.cardOrd,
            this.toAnkiEase(), System.currentTimeMillis() - this.timeTaken)
    }

    private fun ReviewAction.Review.toAnkiEase(): Int {
        log("Provider: toAnkiEase entered - cardId: ${this.card.noteId} - cardButtonCount: ${this.card.buttonCount}")
        return when {
            this.card.buttonCount == 3 && this.ease > 1 -> this.ease - 1
            else -> this.ease
        }
    }
}


class LocalPersistanceReviewTracker(
    private val repo: Repository,
    cacheSize: Int = 3
) : ReviewTracker() {

    companion object {
        private const val MAX_TIME: Long = 60_000L
    }

    private val cache = Cache<ReviewAction>(cacheSize) {
        log("Tracker: onOverflow - cardId: ${it.card.noteId}")
        GlobalScope.launch(Dispatchers.IO) { repo.insertCardInDb(it.toDbCard()) }
    }

    private var flagged: Boolean = false
    private var cardStartTime: Long = -1L
    private lateinit var currentCard: Card

    override var onAnswer: (Card, Int) -> Unit = { card, ease ->
        log("Tracker: onAnswer - cardId: ${card.noteId} - ease: $ease")
        val timeTaken = Math.min(System.currentTimeMillis() - cardStartTime, MAX_TIME)
        cache.add(ReviewAction.Review(currentCard, ease, timeTaken, flagged))
    }

    override var onSkip: (Card) -> Unit = { cache.add(ReviewAction.Skip(currentCard, flagged)) }

    override var onFlag: (Card) -> Unit = { flagged = !flagged }

    override var onPrevious: () -> Unit = { cache.previous() }

    override var onNext: (Card) -> Unit = {
        log("Tracker: onNext - cardId: ${it.noteId}")
        currentCard = it
        cardStartTime = System.currentTimeMillis()
    }

    override var onComplete: () -> Unit = { cache.flush() }

    private sealed class ReviewAction(val card: Card) {
        class Review(card: Card, val ease: Int, val timeTaken: Long, var flagged: Boolean = false) : ReviewAction(card)
        class Skip(card: Card, var flagged: Boolean = false) : ReviewAction(card)
    }

    private fun ReviewAction.toDbCard(): DbCard = when (this) {
        is ReviewAction.Skip -> DbCard(this.card.noteId, this.card.cardOrd, this.card.buttonCount,
            this.card.question, this.card.answer, this.flagged, -1, -1, Date(System.currentTimeMillis()))
        is ReviewAction.Review -> DbCard(this.card.noteId, this.card.cardOrd, this.card.buttonCount,
            this.card.question, this.card.answer, this.flagged, this.ease, this.timeTaken,
            Date(System.currentTimeMillis()))
    }
}