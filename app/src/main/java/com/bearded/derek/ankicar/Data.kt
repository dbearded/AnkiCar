package com.bearded.derek.ankicar

class ReviewInfo(val noteId: Long, val cardOrd: Long, val buttonCount: Long, val nextReviewTimes: String)

data class AnkiCard(val noteId: Long,
                    var modelId: Long,
                    val cardOrd: Long,
                    val cardName: String,
                    val did: String,
                    val question: String,
                    val answer: String,
                    val questionSimple: String,
                    val answerSimple: String,
                    val answerPure: String)


data class QAPair(val question: String, val answer: String)
const val MASKED_FIELD_QUESTION = "blank"
const val NBSP = "&nbsp;"
interface ModelCleanser {
    fun cleanse(ankiCard: AnkiCard): QAPair

}
fun AnkiCard.getCleanser() = when {
    (modelId == ClozeStatementCleanser.MODEL_ID) -> ClozeStatementCleanser
    else -> { object : ModelCleanser {
        override fun cleanse(ankiCard: AnkiCard): QAPair {
            return QAPair("DUMMY","DUMMY")
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
        val question = ankiCard.questionSimple.replace(RAW_QUESTION_PATTERN, MASKED_FIELD_QUESTION, false).replace(NBSP, "")
        val answer = ankiCard.answerSimple.substringAfter(RAW_ANSWER_PATTERN_A).substringBefore(RAW_ANSWER_PATTERN_B)
//      AdapterView's interface <span class=cloze>[...]</span> has the following parameters for method onItemClick(AdapterView&lt;?&gt;, View, int position, long id)&nbsp;
//      AdapterView's interface <span class=cloze>OnItemClickListener</span> has the following parameters for method onItemClick(AdapterView&lt;?&gt;, View, int position, long id)&nbsp;<br>
        return QAPair(question, answer)
    }

}

class Card private constructor(val noteId: Long,
           val cardOrd: Long,
           val question: String,
           val answer: String) {
    companion object {
        fun AnkiCard.convert(cleanser: ModelCleanser): Card {
            val qaPair = cleanser.cleanse(this)
            return Card(noteId,
                    cardOrd,
                    qaPair.question,
                    qaPair.answer)
        }
    }
}