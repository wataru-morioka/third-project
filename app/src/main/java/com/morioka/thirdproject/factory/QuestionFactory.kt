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
    fun getQuestionById(questionId: Long): Question

    @Query("SELECT * FROM question where questionSeq = :questionSeq and id = 8")
    fun getQuestionBySeq(questionSeq: Long): Question

    @Query("SELECT * FROM question where owner = :owner order by createdDateTime")
    fun getOthersQuestions(owner: String): List<Question>

    @Query("SELECT count(1) FROM question where questionSeq = :questionSeq")
    fun getAlreadyCount(questionSeq: Long): Int

    @Delete
    fun delete(question: Question)

}