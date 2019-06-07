package com.morioka.thirdproject.factory

import android.arch.persistence.room.*
import com.morioka.thirdproject.model.Question

@Dao
interface QuestionFactory {

    @Insert
    fun insert(question: Question)

    @Update
    fun update(question: Question)

    @Query("SELECT count(1) FROM question")
    fun getCount(): Int

    @Delete
    fun delete(question: Question)

}