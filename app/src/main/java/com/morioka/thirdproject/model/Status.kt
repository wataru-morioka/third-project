package com.morioka.thirdproject.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
class Status {
    @PrimaryKey
    var status: Int = 0
    var name: String = ""
    var targetNumber: Int = 0
}