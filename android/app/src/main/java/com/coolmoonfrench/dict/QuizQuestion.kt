package com.coolmoonfrench.dict

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correct: String,
    val explanation: String
)

enum class QuizCategory(val title: String) {
    TENSE("时态练习"),
    WORD("单词练习"),
    PREPOSITION("介词练习"),
    CONJUNCTION("连接词练习"),
    ADVERB("副词练习"),
    PRONOUN("代词练习")
}

fun getQuestions(category: QuizCategory): List<QuizQuestion> = when (category) {
    QuizCategory.TENSE -> tenseQuestions
    QuizCategory.WORD -> wordQuestions
    QuizCategory.PREPOSITION -> prepositionQuestions
    QuizCategory.CONJUNCTION -> conjunctionQuestions
    QuizCategory.ADVERB -> adverbQuestions
    QuizCategory.PRONOUN -> pronounQuestions
}