package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.*
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import authen.*
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.common.SingletonService
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.register_user.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import java.security.Security
import java.text.SimpleDateFormat
import java.util.*
import android.os.Vibrator
import com.morioka.thirdproject.model.UserInfo
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import kotlinx.io.InputStream
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class RegisterUserActivity : AppCompatActivity() {
    private var _sessionId: String? = null
    private var _token: String? = null
    private var _dbContext: AppDatabase? = null
    private var _status: Int = 0
    private val _dialog = ProgressDialog()
    private var _vib: Vibrator? = null
    private var _vibrationEffect: VibrationEffect? = null
    private var _userId: String? = null

    //初期画面条件分岐
    inner class CheckMyInfoAsyncTask : AsyncTask<Void, Int, Boolean>() {
        override fun onPreExecute() {
        }

        override fun doInBackground(vararg param: Void?): Boolean {
            val count = _dbContext!!.userFactory().getCount()

            //新規ユーザだった場合、登録画面表示
            if (count == 0) {
                return false
            }

            //ユーザ登録済みだった場合、サーバにセッションをもらいメイン画面へ遷移
//            login()
            val userInfo = CommonService().login(_dbContext!!)
            _sessionId = userInfo?.sessionId
            _status = userInfo?.status ?: 0
            _userId = userInfo?.userId

            //メイン画面へ遷移
            moveToMainActivity(userInfo)
            return true
        }
        override fun onProgressUpdate(vararg values: Int?) {
        }

        override fun onPostExecute(result: Boolean) {
            if (result){
                return
            }

            //ユーザ登録画面表示
            setContentView(R.layout.register_user)
            register_bt.setOnClickListener {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    _vib?.vibrate(_vibrationEffect)
                } else {
                    _vib?.vibrate(100)
                }
                runOnUiThread {
                    val userId = registration_id.text.toString()
                    if (userId.isEmpty()){
                        Toast.makeText(this@RegisterUserActivity, "IDを入力してください", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    // ダイアログを作成して表示
                    AlertDialog.Builder(this@RegisterUserActivity).apply {
                        setMessage("『${userId}』を本当に登録しますか？")
                        setPositiveButton("oK", DialogInterface.OnClickListener { _, _ ->
                            //登録リクエスト非同期通信
                            registerUser()
                        })
                        setNegativeButton("cancel", null)
                        show()
                    }
                }
            }
        }
    }

    //ユーザ登録処理
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        _vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            _vibrationEffect = VibrationEffect.createOneShot(100, DEFAULT_AMPLITUDE)
        }

        //トークン取得
        val _token = CommonService().getToken()

        //ユーザ情報取得
        _sessionId = intent.getStringExtra(SingletonService.SESSION_ID)
        _status = intent.getIntExtra(SingletonService.STATUS, 0)

        val messageFilter = IntentFilter(SingletonService.UPDATE_TOKEN)
        // Broadcast を受け取る BroadcastReceiver を設定
        // LocalBroadcast の設定
        LocalBroadcastManager.getInstance(this).registerReceiver(UpdateTokenReceiver(), messageFilter)

        //TODO 初期画面表示→徐々に変化
        setContentView(R.layout.init)

        _dbContext = CommonService().getDbContext(this)

        //ユーザ登録済みかどうか確認
        CheckMyInfoAsyncTask().execute()
    }



    override fun onRestart() {
        super.onRestart()
        println("RegisterUserActivity再開")
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        //intent.putExtra("USER_ID", userId)
        intent.putExtra(SingletonService.SESSION_ID, _sessionId)
        intent.putExtra(SingletonService.STATUS, _status)
        intent.putExtra(SingletonService.USER_ID, _userId)
        startActivity(intent)
    }


    override fun onDestroy() {
        super.onDestroy()

        //TODO アプリをアンインストールした際の処理
        println("アプリ終了")
    }


//    //ログイン処理
//    private fun login() {
//        println("ログイン処理開始")
//
//        val user = _dbContext!!.userFactory().getMyInfo()
//        _userId = user.userId
//        var token = CommonService().getToken()
//        if (token.isNullOrEmpty()){
//            token = _token
//        }
//
////
////        val fileInputStream = FileInputStream(File(classLoader.getResource("grpc-server.crt").file)
//
////        val cert2 = CertificateFactory.getInstance("X.509").generateCertificate(fileInputStream)
////
////        val test2: X509Certificate? = null
////
////        val authenServer = NettyChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
////            .sslContext(
////                GrpcSslContexts.forClient()
////                    .trustManager(File(classLoader.getResource("ca.crt").file))
////                    .build())
////            .build()
//
//
////        val authenServer = NettyChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
////            .sslContext(
////                GrpcSslContexts.forClient()
////                    .trustManager(FileInputStream("/src/main/resources"))
////                    .build())
////            .build()
//
////        val authenServer = NettyChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
////                                              .negotiationType(NegotiationType.TLS)
////                                              .sslContext(GrpcSslContexts.forClient().ciphers(null).build())
////                                              .build()
//
////        val authenServer = NettyChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
////            .negotiationType(NegotiationType.TLS)
////            .useTransportSecurity()
////            .build()
//////
//        val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
//            .usePlaintext()
//            .build()
//
//        val agent = AuthenGrpc.newStub(authenServer)
//
//        val request = LoginRequest.newBuilder()
//            .setUserId(user.userId)
//            .setPassword(user.password)
//            .setToken(token)
//            .build()
//
//        var result = false
//
//        agent.login(request, object : StreamObserver<LoginResult> {
//            override fun onNext(reply: LoginResult) {
//                println("res : " + reply.sessionId + reply.status)
//                result = reply.result
//                _sessionId = reply.sessionId
//                _status = reply.status
//            }
//
//            override fun onError(t: Throwable?) {
//                println("ログイン処理失敗")
//                //メイン画面へ遷移
//                moveToMainActivity(user)
//
//                authenServer.shutdown()
//            }
//
//            override fun onCompleted() {
//                if (!result) {
//                    runOnUiThread {
//                        Toast.makeText(this@RegisterUserActivity, "無効なアカウントです", Toast.LENGTH_SHORT).show()
//                    }
//                    return
//                }
//
//                //メイン画面へ遷移
//                moveToMainActivity(user)
//
//                authenServer.shutdown()
//            }
//        })
//    }

    //メイン画面へ遷移
    private fun moveToMainActivity(userInfo: UserInfo?) {
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        intent.putExtra(SingletonService.SESSION_ID, userInfo?.sessionId)
        intent.putExtra(SingletonService.STATUS, userInfo?.status)
        intent.putExtra(SingletonService.USER_ID, userInfo?.userId)
        startActivity(intent)
    }

    //ユーザ登録処理
    private fun registerUser() {
        println("ユーザ登録処理開始")
        _dialog.show(supportFragmentManager, "test")

        var token = CommonService().getToken()
        if (token.isNullOrEmpty()) {
            token = _token
            if(token.isNullOrEmpty()){
                token = ""
            }
        }

        //TODO 暗号化
        val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            .usePlaintext()
            .build()

        val agent = AuthenGrpc.newBlockingStub(authenServer)

        _userId = registration_id.text.toString()
        val request = RegistrationRequest.newBuilder()
            .setUserId(_userId)
            .setToken(token)
            .build()

        val response: RegistrationResult
        try{
            response = agent.register(request)
            _sessionId = response.sessionId
        } catch (e: Exception){
            _dialog.dismiss()
            Toast.makeText(this@RegisterUserActivity, "サーバに接続できません", Toast.LENGTH_SHORT).show()
            authenServer.shutdown()
            return
        }

        if (!response.result){
            _dialog.dismiss()
            Toast.makeText(this@RegisterUserActivity, "そのIDはすでに登録されています", Toast.LENGTH_SHORT).show()
            authenServer.shutdown()
            return
        }

        //ユーザ情報を登録
        val user = User()
        user.userId = _userId ?: ""
        user.password = response.password
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())
        user.createdDateTime = now
        runBlocking {
            GlobalScope.launch {
                _dbContext!!.userFactory().insert(user)
            }.join()
        }

        _dialog.dismiss()

        Toast.makeText(this@RegisterUserActivity, "登録が完了いたしました", Toast.LENGTH_SHORT).show()

        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        //intent.putExtra("USER_ID", userId)
        intent.putExtra(SingletonService.SESSION_ID, _sessionId)
        intent.putExtra(SingletonService.STATUS, 0)
        intent.putExtra(SingletonService.USER_ID, _userId)
        startActivity(intent)

        authenServer.shutdown()
    }

    //トークンが更新されたことを検知
    inner class UpdateTokenReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DataReceiver", "onReceive")

            // Broadcast されたメッセージを取り出す
            _token = intent.getStringExtra(SingletonService.TOKEN)
        }
    }
}