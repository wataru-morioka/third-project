package com.morioka.thirdproject.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
class Question {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
    @ColumnInfo(index = true)
    var questionSeq: Long = 0
    @ColumnInfo(index = true)
    var owner: String = ""
    var question: String = ""
    var answer1: String = ""
    var answer2: String = ""
    var answer1number: Int = 0
    var answer2number: Int = 0
    var myDecision: Int = 0
    var targetNumber: Int = 0
    var timePeriod: Int = 0
    var timeLimit: String? = null
    var confirmationFlag: Boolean = false
    var determinationFlag: Boolean = false
    @ColumnInfo(index = true)
    var createdDateTime: String = ""
    @ColumnInfo(index = true)
    var modifiedDateTime: String? = null
    var deleteFlag: Boolean? = false
}