package com.morioka.thirdproject.common

import android.app.Activity
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
import android.os.VibrationEffect
import android.util.Log
import android.widget.Toast
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.model.UserInfo
import com.morioka.thirdproject.ui.MainActivity
import com.squareup.okhttp.ConnectionSpec
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.async
import java.lang.Exception
import kotlinx.io.InputStream
import java.io.BufferedInputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.collections.ArrayList


class CommonService {
    private var mActivity: Activity? = null

    constructor(){
        return
    }

    constructor(activity: Activity) {
        mActivity = activity
    }

    fun getStatusData(): ArrayList<Target> {
        return ArrayList<Target>().apply {
            add(Target(0, "Bronze", 10))
            add(Target(1, "Silver", 50))
            add(Target(2, "Gold", 100))
        }
    }

    fun getDbContext(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, SingletonService.SQLITE_DATABASE_NAME)
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

            //トークン取得イベント周知
            messageIntent.putExtra(SingletonService.TOKEN, token)
            SingletonService.getAppContext().sendBroadcast(messageIntent)
        }
        return ""
    }

    //SSLSocketFactory生成
    fun createSocketFactory(): SSLSocketFactory? {
        val sslSocketFactory: SSLSocketFactory?
        try {
            val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
            val caInput: InputStream = BufferedInputStream(SingletonService.getAppContext().classLoader.getResourceAsStream("ca-cert.pem"))

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
    fun login(_dbContext: AppDatabase): UserInfo {
        println("ログイン処理開始")

        val token = getToken()

        val user = runBlocking {
            GlobalScope.async {
                _dbContext.userFactory().getMyInfo()
            }.await()
        }

        val userId = user.userId

        val authenChannel = getGRPCChannel(SingletonService.HOST, SingletonService.AUTHEN_PORT)
        val agent = AuthenGrpc.newBlockingStub(authenChannel)

        val request = LoginRequest.newBuilder()
            .setUserId(user.userId)
            .setPassword(user.password)
            .setToken(token ?: "")
            .build()

        val response: LoginResult

        try {
            response = agent.login(request)
        } catch (e: Exception) {
            println("ログイン処理中サーバとの接続に失敗")
            return UserInfo(token, userId, null, user.status)
        } finally {
            authenChannel.shutdown()
        }

        if (!response.result) {
            println("ログイン処理中サーバ内部エラー")
            mActivity?.runOnUiThread{
                Toast.makeText(mActivity, "ログイン処理中サーバ内部エラー", Toast.LENGTH_SHORT).show()
            }
            return UserInfo(token, userId, null, user.status)
        }

        println("res : " + response.sessionId + response.status)

        return UserInfo(token, userId, response.sessionId, response.status)
    }

    //メッセージングサーバの接続情報を取得
    fun getFactory(): ConnectionFactory {
        return ConnectionFactory().apply {
            host = SingletonService.HOST
            port = SingletonService.RABBITMQ_PORT
            useSslProtocol("TLSv1.2")
            virtualHost = SingletonService.VIRTUAL_HOST
            username = SingletonService.RABBITMQ_USER
            password = SingletonService.RABBITMQ_PASSWORD
        }
    }

    fun isOnline(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        // You should always check isConnected(), since isConnected()
        // handles cases like unstable network state.
        return networkInfo != null && networkInfo.isConnected
    }

    fun getVibEffect() : VibrationEffect? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        return null
    }

    fun getNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())
    }

    fun changeDateFormat(target: String): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(getDateTime(target))
    }

    fun getDateTime(target: String): Date {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).parse(target)
    }

    fun getGRPCChannel(host: String, port: Int): ManagedChannel {
        return OkHttpChannelBuilder.forAddress(host, port)
            .connectionSpec(ConnectionSpec.COMPATIBLE_TLS)
            .sslSocketFactory(createSocketFactory())
            .enableRetry()
            .keepAliveWithoutCalls(true)
            .keepAliveTime(120, TimeUnit.SECONDS)
//            .keepAliveTimeout(150, TimeUnit.SECONDS)
            .build()
//        return ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
//                .usePlaintext()
//                .build()
    }
}