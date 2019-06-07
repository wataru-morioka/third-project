package com.morioka.thirdproject.model

data class AnswerRequest(
    val questionSeq: Long,
    val userId: String,
    val decision: Int,
    val timeLimit: String
)