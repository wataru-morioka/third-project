package com.morioka.thirdproject.service

import android.app.PendingIntent.getActivity
import android.arch.persistence.room.Room
import android.content.Context
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Target

class CommonService {
    fun getStatusData(): ArrayList<Target> {
        val statusList = ArrayList<Target>()
        statusList.add(Target(0, "bronze", 10))
        statusList.add(Target(1, "silver", 50))
        statusList.add(Target(2, "gold", 100))
        return statusList
    }

    fun getDbContext(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "thirdProject2").build()
    }
}