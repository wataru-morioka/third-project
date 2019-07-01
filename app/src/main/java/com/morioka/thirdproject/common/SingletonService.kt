package com.morioka.thirdproject.common

import android.app.Application
import android.content.Context

class SingletonService : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        const val HOST = "192.168.0.100"
        const val VIRTUAL_HOST = "/third-project"
        const val RABBITMQ_USER  = "rabbitmq"
        const val RABBITMQ_PASSWORD  = "rabbitmq"
        const val SQLITE_DATABASE_NAME = "thirdProject3"
        const val GRPC_PORT = 31100
        const val AUTHEN_PORT = 31000
        const val RABBITMQ_PORT = 31200
        const val UPDATE_TOKEN = "update_token"
        const val OWN = "own"
        const val OTHERS = "others"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        const val TOKEN = "TOKEN"
        const val SESSION_ID = "SESSION_ID"
        const val STATUS = "STATUS"
        const val USER_ID = "USER_ID"
        const val QUESTION_ID = "QUESTION_ID"
        const val TIME_PERIOD = 3
        const val CURRENT_POSITION = "CURRENT_POSITION"
        const val ACTIVE_CHECK = "activeCheck"
        const val PRIVACY_POLICY_URL = "http://wtrmorioka.html.xdomain.jp/policy.html"

        var instance: SingletonService? = null
            private set

        fun getAppContext(): Context {
            return instance!!.applicationContext
        }
    }
}