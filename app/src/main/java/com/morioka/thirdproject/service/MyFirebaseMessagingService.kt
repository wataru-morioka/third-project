package com.morioka.thirdproject.service

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.common.SingletonService

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String?) {
        // 端末＋アプリを一意に識別するためのトークンを取得
        println("トークンが更新されました")
        Log.i("FIREBASE", "[SERVICE] Token = ${token ?: "Empty"}")

        // Local Broadcast で発信する（activityも再描画させる）
        val messageIntent = Intent(SingletonService.UPDATE_TOKEN)
        messageIntent.putExtra(SingletonService.TOKEN, token)
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        remoteMessage?.let { message ->
            // 通知メッセージ
            message.notification?.let {
                // 通知メッセージを処理
                println("プッシュ通知を受信")
            }

            // データメッセージ
            message.data?.let {
                // データメッセージを処理
                println("データプッシュ通知を受信")
            }
        }
    }
}