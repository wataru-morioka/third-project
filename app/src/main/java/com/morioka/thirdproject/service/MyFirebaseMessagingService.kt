package com.morioka.thirdproject.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String?) {
        // 端末＋アプリを一意に識別するためのトークンを取得
        println("トークンが更新されました")
        Log.i("FIREBASE", "[SERVICE] Token = ${token ?: "Empty"}")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        remoteMessage?.let { message ->
            // 通知メッセージ
            message.notification?.let {
                // 通知メッセージを処理
            }

            // データメッセージ
            message.data?.let {
                // データメッセージを処理
            }
        }
    }
}