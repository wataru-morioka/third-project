package com.morioka.thirdproject.common

import android.arch.persistence.room.Room
import android.content.Context
import authen.AuthenGrpc
import authen.LogoutRequest
import authen.LogoutResult
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Target
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
        return Room.databaseBuilder(context, AppDatabase::class.java, "thirdProject2").build()
    }

    //ログアウト処理
    fun logout(sessionId: String) {
        println("ログアウト処理")
        val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            .usePlaintext()
            .build()
        val agent = AuthenGrpc.newStub(authenServer)

        val request = LogoutRequest.newBuilder()
            .setSessionId(sessionId)
            .build()

        var result = false

        runBlocking {
            GlobalScope.launch {
                agent.logout(request, object : StreamObserver<LogoutResult> {
                    override fun onNext(reply: LogoutResult) {
                        println("res : " + reply.result)
                        result = reply.result
                    }

                    override fun onError(t: Throwable?) {
                        authenServer.shutdown()
                    }

                    override fun onCompleted() {
                        if (!result) {
                            println("キャッシュクリアに失敗しました")
                        }
                        authenServer.shutdown()
                    }
                })
            }.join()
        }
    }
}