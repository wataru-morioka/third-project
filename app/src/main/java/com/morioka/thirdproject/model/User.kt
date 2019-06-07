package com.morioka.thirdproject.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
class User {
    @PrimaryKey
    var id: Long = 0
    var userId: String = ""
    var registrationId: String? = null
    var password: String = ""
    var sessionId: String? = null
    var status: Int = 0
    var createdDateTime: String = ""
    var modifiedDateTime: String? = null
}