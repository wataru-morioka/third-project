package com.morioka.thirdproject.factory

import android.arch.persistence.room.*
import com.morioka.thirdproject.model.Question

@Dao
interface QuestionFactory {

    @Insert
    fun insert(question: Question): Long

    @Update
    fun update(question: Question)

    @Query("SELECT count(1) FROM question")
    fun getCount(): Int

    @Query("SELECT * FROM question where Id = :questionId")
    fun getQuestion(questionId: Long): Question

    @Query("SELECT * FROM question where owner = :owner")
    fun getOthersQuestions(owner: String): List<Question>

    @Query("SELECT count(1) FROM question where questionSeq = :questionSeq")
    fun getSameCount(questionSeq: Long): Int

    @Delete
    fun delete(question: Question)

}