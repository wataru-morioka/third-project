package com.morioka.thirdproject.common

import android.arch.persistence.room.Room
import android.content.Context
import android.content.Intent
import authen.*
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Target
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.net.ConnectivityManager
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.model.UserInfo
import com.squareup.okhttp.ConnectionSpec
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.okhttp.internal.Platform
import java.lang.Exception
import io.grpc.internal.GrpcUtil
import kotlinx.io.InputStream
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory


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

    //SSLSocketFactory生成
    fun createSocketFactory(): SSLSocketFactory? {
        var sslSocketFactory: SSLSocketFactory? = null
        try {
            val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
            // From https://www.washington.edu/itconnect/security/ca/load-der.crt
            val caInput: InputStream = BufferedInputStream(SingletonService().getAppContext().classLoader.getResourceAsStream("ca-cert.pem"))

//            val inputAsString = caInput.bufferedReader().use { it.readText() }

            val ca: X509Certificate = caInput.use {
                cf.generateCertificate(it) as X509Certificate
            }
            System.out.println("SocketFactory生成成功：ca=" + ca.subjectDN)

            // Create a KeyStore containing our trusted CAs
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType).apply {
                load(null, null)
                setCertificateEntry("ca", ca)
            }

            // Create a TrustManager that trusts the CAs inputStream our KeyStore
            val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
            val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(keyStore)
            }

            // Create an SSLContext that uses our TrustManager
            val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
                init(null, tmf.trustManagers, null)
            }
            sslSocketFactory = sslContext.socketFactory
            return sslSocketFactory
        } catch (gse: GeneralSecurityException) {
            throw RuntimeException("TLS Provider failure", gse)
        }
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
//            authenChannel = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
//                .usePlaintext()
//                .build()
            authenChannel = OkHttpChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
                .connectionSpec(ConnectionSpec.COMPATIBLE_TLS)
                .sslSocketFactory(createSocketFactory())
                .build()
        } catch (e: Exception) {
            println("サーバに接続に失敗")
            e.printStackTrace()
            authenChannel?.shutdown()
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