package com.morioka.thirdproject.service

import android.app.Notification
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.morioka.thirdproject.common.SingletonService
import android.support.v4.app.NotificationManagerCompat
import android.app.PendingIntent
import android.support.v4.app.NotificationCompat
import com.morioka.thirdproject.R
import com.morioka.thirdproject.ui.RegisterUserActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String?) {
        // 端末＋アプリを一意に識別するためのトークンを取得
        println("トークンが更新されました")
        Log.i("FIREBASE", "[SERVICE] Token = ${token ?: "Empty"}")

        //トークン更新イベントを周知
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
                notify(message)
            }
        }
    }

    private fun notify(remoteMessage: RemoteMessage){
        // プッシュメッセージのdataに含めた値を取得
        val data = remoteMessage.data
        val type = data["type"]

        //トークン有効チェックの場合
        if (type == SingletonService.ACTIVE_CHECK) {
            return
        }

        val owner = data["owner"]
//        val questionId = data["questionId"]
        val questionSeq = Integer.parseInt(data["questionSeq"]!!)
        val question = data["question"]

        println("プッシュ通知検知:$owner")

        // Notificationを生成
        val builder = NotificationCompat.Builder(applicationContext).also {
            it.setSmallIcon(R.mipmap.ic_launcher)
            if (type == "new") {
                it.setContentTitle("新着の質問があります") // 2行目
            } else {
                it.setContentTitle("回答の集計が完了しました") // 2行目
            }

            if (owner == SingletonService.OWN) {
                it.setSubText("自分の質問の回答集計が完了") // 3行目
            } else {
                it.setSubText("相手の質問に新着情報") // 3行目
            }
            it.setContentInfo("info") // 右端
            it.setContentText(question)
            it.setDefaults(
                Notification.DEFAULT_SOUND
                        or Notification.DEFAULT_VIBRATE
                        or Notification.DEFAULT_LIGHTS
            )
            it.setAutoCancel(true)

            // タップ時に呼ばれるIntentを生成
            val intent = Intent(this, RegisterUserActivity::class.java)
            val contentIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            it.setContentIntent(contentIntent)
        }

        // Notification表示
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.notify(questionSeq, builder.build())
    }
}