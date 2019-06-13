package com.morioka.thirdproject.service

import android.app.Notification
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.common.SingletonService
import android.support.v4.app.NotificationManagerCompat
import android.app.PendingIntent
import android.support.annotation.IntegerRes
import android.support.v4.app.NotificationCompat
import com.morioka.thirdproject.R
import com.morioka.thirdproject.ui.RegisterUserActivity

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
                println("データプッシュ通知を受信")
                notify(remoteMessage)
            }
        }
    }

    private fun notify(remoteMessage: RemoteMessage){
        // プッシュメッセージのdataに含めた値を取得
        val data = remoteMessage.data
        val owner = data["owner"]
        val type = data["type"]
//        val questionId = data["questionId"]
        val questionSeq = Integer.parseInt(data["questionSeq"]!!)
        val question = data["question"]

        println("プッシュ検知:" + data["owner"])

        // Notificationを生成
        val builder = NotificationCompat.Builder(applicationContext)
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.setContentTitle(getString(R.string.app_name))
//                builder.setContentText(remoteMessage.notification!!.body)
        if (type == "new") {
            builder.setContentText("新着の質問があります") // 2行目
        } else {
            builder.setContentText("回答の集計が完了しました") // 2行目
        }

        if (owner == SingletonService.OWN) {
            builder.setSubText("自分の質問") // 3行目
        } else {
            builder.setSubText("相手の質問") // 3行目
        }
        builder.setContentInfo("info") // 右端
        builder.setContentText(question)
        builder.setDefaults(
            Notification.DEFAULT_SOUND
                    or Notification.DEFAULT_VIBRATE
                    or Notification.DEFAULT_LIGHTS
        )
        builder.setAutoCancel(true)

        // タップ時に呼ばれるIntentを生成
        val intent = Intent(this, RegisterUserActivity::class.java)
//                intent.putExtra(SingletonService.SESSION_ID, _sessionId)
//                intent.putExtra(SingletonService.STATUS, _status)
        val contentIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(contentIntent)

        // Notification表示
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(questionSeq, builder.build())
    }
}