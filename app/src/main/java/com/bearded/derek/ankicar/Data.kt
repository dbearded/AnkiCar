package com.bearded.derek.ankicar

import android.util.EventLogTags
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.regex.Pattern

sealed class AnkiReviewCard {
    class AnkiCardForReview(val noteId: Long, val cardOrd: Int, val buttonCount: Long, val nextReviewTimes: String)
    class AnkiCardReviewed(val noteId: Long, val cardOrd: Int, val ease: String, val timeTaken: String)
}

data class AnkiCard(val noteId: Long,
                    var modelId: Long,
                    val cardOrd: Int,
                    val cardName: String,
                    val did: String,
                    val question: String,
                    val answer: String,
                    val questionSimple: String,
                    val answerSimple: String,
                    val answerPure: String)

object Deck {
    const val ID_DEVELOPER = 1L
    const val ID_TEMP = 1523336138544L
}

data class QAPair(val question: String, val answer: String)
const val MASKED_FIELD_QUESTION = "blank"
const val NBSP = "&nbsp;"
interface ModelCleanser {
    fun cleanse(ankiCard: AnkiCard): QAPair

}
fun AnkiCard.getCleanser() = when {
    (modelId == ClozeStatementCleanser.MODEL_ID) -> ClozeStatementCleanser
    (modelId == ClozeOverlappingCleanser.MODEL_ID) -> ClozeOverlappingCleanser
    (modelId == ProblemCleanser.MODEL_ID) -> ProblemCleanser
    else -> {
        object : ModelCleanser {
            override fun cleanse(ankiCard: AnkiCard): QAPair {
                return QAPair("DUMMY", "DUMMY")
            }
        }
    }
}

object ClozeStatementCleanser : ModelCleanser {
    const val MODEL_ID = 1506641314345L
    val RAW_QUESTION_PATTERN = "<span class=cloze>[...]</span>"

    val RAW_ANSWER_PATTERN_A = "<span class=cloze>"
    val RAW_ANSWER_PATTERN_B = "</span>"

    override fun cleanse(ankiCard: AnkiCard): QAPair {
        val question = Jsoup.parse(ankiCard.questionSimple).text().replace("[...]", "blank")
        val answer = ankiCard.answerSimple.substringAfter(RAW_ANSWER_PATTERN_A).substringBefore(RAW_ANSWER_PATTERN_B)
        return QAPair(question, answer)
    }

}

object ClozeOverlappingCleanser : ModelCleanser {
    const val MODEL_ID = 1522201265289L
    override fun cleanse(ankiCard: AnkiCard): QAPair {
        val back: Document = Jsoup.parse(ankiCard.answer)
        val front: Document = Jsoup.parse(ankiCard.question)
        val title = back.getElementsByClass("title").first()
        val items = getItems("front", front)
        val question = buildQuestion(title, items)
        val answer = back.getElementsByClass("cloze").text()
        val i = 0
        return QAPair(question, answer)
    }

    fun buildQuestion(title: Element, items: Elements): String {
        return buildString {
            append(titleToString(title))
            append(": \n")
            append(itemsToString(items))
        }
    }

    fun getItems(side: String, doc: Document): Elements {
        return doc.getElementsByClass(side).first().selectFirst("[class='text']").children()
                .select("div:not([class='hidden'], [class='hidden'] *)")
    }

    fun titleToString(title: Element): String {
        return title.text().replace("\n", "")
    }

    fun itemsToString(items: Elements): String {
        val CLOZE = "[...]"
        val HIDDEN = "..."
        val HIDDEN_REPLACEMENT = " of " + items.size.toString()
        var clozeReached = false
        var firstHidden = false
        return items.withIndex().joinToString { (index, value) ->
            when (value.text()) {
                CLOZE -> {
                                clozeReached = true
                    "blank"
                }
                HIDDEN -> {
                    if (!firstHidden) {
                        firstHidden = true
                        (index + 1).toString() + HIDDEN_REPLACEMENT
                    } else {
                        ""
                    }

                }
                else -> value.text()
            }
        }
    }
}

object ProblemCleanser : ModelCleanser {
    const val MODEL_ID = 1521746738580L
    override fun cleanse(ankiCard: AnkiCard): QAPair {
        val question = Jsoup.parse(ankiCard.questionSimple).text().replace("Approach: [...]", "")
        return QAPair(question, ankiCard.answerPure)
    }

}

data class Card private constructor(val noteId: Long,
           val cardOrd: Int,
           val question: String,
           val answer: String) {
    companion object {
        fun AnkiCard.build(cleanser: ModelCleanser): Card {
            val qaPair = cleanser.cleanse(this)
            return Card(noteId,
                    cardOrd,
                    qaPair.question,
                    qaPair.answer)
        }
    }
}