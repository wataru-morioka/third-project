package com.morioka.thirdproject.factory

import android.arch.persistence.room.*
import com.morioka.thirdproject.model.Status

@Dao
interface StatusFactory {
    @Insert
    fun insert(status: Status)

    @Update
    fun update(status: Status)

    @Query("SELECT * FROM status")
    fun getStatusInfo(): Status

    @Delete
    fun delete(status: Status)
}