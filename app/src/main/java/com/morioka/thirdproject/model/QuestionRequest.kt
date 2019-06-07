package com.morioka.thirdproject.model

data class QuestionRequest(
    val questionId: Long,
    val question: String,
    val answer1: String,
    val answer2: String,
    val targetNumber: Int,
    val timePeriod: Int
)