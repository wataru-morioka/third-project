package com.morioka.thirdproject.factory

import android.arch.persistence.room.*
import com.morioka.thirdproject.model.User

@Dao
interface UserFactory {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(user: User)

    @Update
    fun update(user: User)

    @Query("SELECT * FROM user")
    fun getMyInfo(): User

    @Query("SELECT count(1) FROM user")
    fun getCount(): Int

    @Delete
    fun delete(user: User)

}