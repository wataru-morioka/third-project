package com.morioka.thirdproject.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import authen.AuthenGrpc
import authen.LoginRequest
import authen.LoginResult
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.User
import com.squareup.okhttp.ConnectionSpec
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception

class ReceiverService {
    //ネットワーク状態が更新されたことを検知
    class ConnectionReceiver(private val mObserver: Observer) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val info = manager.activeNetworkInfo
            if (info != null && info.isConnected) {
                mObserver.onConnect()
            } else {
                mObserver.onDisconnect()
            }
        }

        //----- コールバックを定義 -----
        interface Observer {
            fun onConnect()
            fun onDisconnect()
        }
    }

    //トークンが更新されたことを検知
    class UpdateTokenReceiver(private val dbContext: AppDatabase): BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("トークンの更新を検知")

            // Broadcast されたメッセージを取り出す
            val token = intent.getStringExtra(SingletonService.TOKEN)

            var user: User? = null
            runBlocking {
                GlobalScope.launch {
                    user = dbContext.userFactory().getMyInfo()
                }.join()
            }

            if (user == null) {
                return
            }

            val request = LoginRequest.newBuilder()
                .setUserId(user?.userId)
                .setPassword(user?.password)
                .setToken(token)
                .build()

            val authenChannel = CommonService().getGRPCChannel(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            val agent = AuthenGrpc.newStub(authenChannel)
            agent.login(request, object : StreamObserver<LoginResult> {
                override fun onNext(reply: LoginResult) {
                    println("res : " + reply.sessionId + reply.status)
                }

                override fun onError(t: Throwable?) {
                    println("エラー：トークンの更新をサーバに送信失敗")
                    authenChannel.shutdown()
                }

                override fun onCompleted() {
                    println("トークンの更新をサーバに送信成功")
                    authenChannel.shutdown()
                }
            })
        }
    }
}