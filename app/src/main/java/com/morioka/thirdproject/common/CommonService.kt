package com.morioka.thirdproject.common

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.migration.Migration
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import authen.*
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Target
import com.rabbitmq.client.ConnectionFactory
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.net.NetworkInfo
import android.net.ConnectivityManager
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.model.UserInfo
import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import java.io.File
import java.io.FileInputStream
import java.lang.Exception

class CommonService {
//    val host: String = "10.0.2.2"
//    val rabbitmqPort = "50030"
//    val grpcPort = 50050
//    val authenPort = 50030

    fun getStatusData(): ArrayList<Target> {
        val statusList = ArrayList<Target>()
        statusList.add(Target(0, "bronze", 10))
        statusList.add(Target(1, "silver", 50))
        statusList.add(Target(2, "gold", 100))
        return statusList
    }

    fun getDbContext(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "thirdProject")
//            .addMigrations(object: Migration (2, 3) {
//                override fun migrate(database: SupportSQLiteDatabase) {
//                    database.execSQL("ALTER TABLE questioni ADD confirmationFlag not null")
//                }
//            })
//            .fallbackToDestructiveMigration()
            .build()
    }

    //トークン取得
    fun getToken(): String? {
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("トークン取得に失敗")
                Log.w("FIREBASE", "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            println("トークン取得")
            Log.i("FIREBASE", "[CALLBACK] Token = ${task.result?.token}")
            val token = task.result?.token
            val messageIntent = Intent(SingletonService.UPDATE_TOKEN)
            messageIntent.putExtra(SingletonService.TOKEN, token)
            SingletonService().getAppContext().sendBroadcast(messageIntent)
        }
        return ""
    }

    //ログイン処理
    fun login(_dbContext: AppDatabase): UserInfo? {
        println("ログイン処理開始")

        val token = getToken()

        var user: User? = null
        runBlocking {
            GlobalScope.launch {
                user = _dbContext.userFactory().getMyInfo()
            }.join()
        }
        val userId = user?.userId

        var authenChannel: ManagedChannel? = null

        try {
            authenChannel = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
                .usePlaintext()
                .build()

//            authenChannel = NettyChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
//                                                .sslContext(
//                                                    GrpcSslContexts.forClient()
//                                                        .trustManager(SingletonService().getAppContext().classLoader.getResourceAsStream("cacert.pem"))
//                                                        .build())
//                                                .build()
        } catch (e: Exception) {
            authenChannel?.shutdown()
            println("サーバに接続に失敗")
            return null
        }

        val agent = AuthenGrpc.newBlockingStub(authenChannel)

        val request = LoginRequest.newBuilder()
            .setUserId(user?.userId)
            .setPassword(user?.password)
            .setToken(token ?: "")
            .build()

        var userInfo: UserInfo? = null

        val response: LoginResult
        try {
            response = agent.login(request)
        } catch (e: Exception) {
            println("ログイン処理中サーバとの接続に失敗")
            authenChannel?.shutdown()
            return userInfo
        }

        println("res : " + response.sessionId + response.status)
        userInfo = UserInfo(token, userId, response.sessionId, response.status)
        authenChannel?.shutdown()

        return userInfo
    }

    //メッセージングサーバの接続情報を取得
    fun getFactory(): ConnectionFactory {
        val factory = ConnectionFactory()
        factory.host = SingletonService.HOST
        factory.port = SingletonService.RABBITMQ_PORT
        factory.useSslProtocol("TLSv1.2")
        factory.virtualHost = SingletonService.VIRTUAL_HOST
        factory.username = SingletonService.RABBITMQ_USER
        factory.password = SingletonService.RABBITMQ_PASSWORD
        return factory
    }

    fun isOnline(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        // You should always check isConnected(), since isConnected()
        // handles cases like unstable network state.
        return networkInfo != null && networkInfo.isConnected
    }
}