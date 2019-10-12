package com.morioka.thirdproject.model

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.morioka.thirdproject.factory.QuestionFactory
import com.morioka.thirdproject.factory.StatusFactory
import com.morioka.thirdproject.factory.UserFactory

@Database(entities = [User::class, Question::class, Status::class], version = 1) // Kotlin 1.2からは arrayOf(User::class)の代わりに[User::class]と書ける
abstract class AppDatabase : RoomDatabase() {

    // DAOを取得する。
    abstract fun userFactory(): UserFactory

    abstract fun questionFactory(): QuestionFactory

    abstract fun statusFactory(): StatusFactory

    // valでも良い。
    // abstract val dao: UserDao
}