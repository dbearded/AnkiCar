package com.bearded.derek.ankicar.model

import android.content.ContentResolver
import com.bearded.derek.ankicar.model.anki.AnkiReviewCard
import com.bearded.derek.ankicar.model.anki.Card
import com.bearded.derek.ankicar.model.anki.queryReviewCards
import com.bearded.derek.ankicar.model.anki.updateAnki
import com.bearded.derek.ankicar.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(
    private val database: AnkiDatabase,
    private val contentResolver: ContentResolver
) {

    suspend fun queryForCards(deckId: Long, limit: Int): List<Card> {
        log("Adapter: queryForCards entered")
        return withContext(Dispatchers.IO) { queryReviewCards(deckId, limit, contentResolver) }
    }

    suspend fun sendReviewToAnki(reviewedCard: AnkiReviewCard.AnkiCardReviewed): Int {
        log("Adapter: sendReviewToAnki entered - cardId: ${reviewedCard.noteId}")
        return withContext(Dispatchers.IO) { updateAnki(reviewedCard, contentResolver) }
    }

    suspend fun sendReviewToAnki(reviewedCards: List<AnkiReviewCard.AnkiCardReviewed>): Int {
        withContext(Dispatchers.IO) { reviewedCards.forEach { sendReviewToAnki(it) } }
        return reviewedCards.size
    }

    suspend fun insertCardInDb(card: DbCard) {
        val tmp = object : TransactionListener {
            override fun onComplete() { }
        }
        withContext(Dispatchers.IO) { insertCard(database, tmp, card) }
    }
}