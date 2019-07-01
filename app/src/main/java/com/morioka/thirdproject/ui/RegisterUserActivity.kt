package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.*
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast
import authen.*
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.common.SingletonService
import kotlinx.android.synthetic.main.register_user.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import java.security.Security
import android.os.Vibrator
import com.morioka.thirdproject.model.UserInfo
import io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory
import io.grpc.netty.shaded.io.netty.util.internal.logging.JdkLoggerFactory
import kotlinx.coroutines.async
import java.lang.Exception

class RegisterUserActivity : AppCompatActivity() {
    private var _sessionId: String? = null
    private var _token: String? = null
    private var _dbContext: AppDatabase? = null
    private var _status: Int = 0
    private val _dialog = ProgressDialog()
    private var _vib: Vibrator? = null
    private var _vibrationEffect: VibrationEffect? = null
    private var _userId: String? = null

//    //初期画面条件分岐
//    inner class CheckMyInfoAsyncTask : AsyncTask<Void, Int, Boolean>() {
//        override fun onPreExecute() {
//        }
//
//        override fun doInBackground(vararg param: Void?): Boolean {
//            val count = _dbContext!!.userFactory().getCount()
//
//            //新規ユーザだった場合、登録画面表示
//            if (count == 0) {
//                return false
//            }
//
//            //ユーザ登録済みだった場合、サーバにセッションをもらいメイン画面へ遷移
//            val userInfo = CommonService(this@RegisterUserActivity).login(_dbContext!!)
//
//            _sessionId = userInfo.sessionId
//            _status = userInfo.status
//            _userId = userInfo.userId
//
//            //メイン画面へ遷移
//            moveToMainActivity(userInfo)
//            return true
//        }
//        override fun onProgressUpdate(vararg values: Int?) {
//        }
//
//        override fun onPostExecute(result: Boolean) {
//            if (result){
//                return
//            }
//
//            //ユーザ登録画面表示
//            setContentView(R.layout.register_user)
//
//            register_bt.setOnClickListener {
//                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
//                    _vib?.vibrate(_vibrationEffect)
//                } else {
//                    _vib?.vibrate(100)
//                }
//
//                val userId = registration_id.text.toString()
//                if (userId.isEmpty()){
//                    Toast.makeText(this@RegisterUserActivity, "IDを入力してください", Toast.LENGTH_SHORT).show()
//                    return@setOnClickListener
//                }
//
//                // ダイアログを作成して表示
//                AlertDialog.Builder(this@RegisterUserActivity).apply {
//                    setMessage("『${userId}』を本当に登録しますか？")
//                    setPositiveButton("oK") { it, _ ->
//                        it.dismiss()
//                        //登録リクエスト非同期通信
//                        registerUser()
//                    }
//                    setNegativeButton("cancel", null)
//                    show()
//                }
//            }
//        }
//    }

    //ユーザ登録済みかどうか確認
    private fun checkMyInfo() {
        val count = _dbContext!!.userFactory().getCount()

        //すでに登録されている場合、メイン画面へ遷移
        if (count != 0) {
            //ユーザ登録済みだった場合、サーバにセッションをもらいメイン画面へ遷移
            val userInfo = CommonService(this@RegisterUserActivity).login(_dbContext!!)

            _sessionId = userInfo.sessionId
            _status = userInfo.status
            _userId = userInfo.userId

            //メイン画面へ遷移
            moveToMainActivity(userInfo)
            return
        }

        runOnUiThread {
            //ユーザ登録画面表示
            setContentView(R.layout.register_user)

            register_bt.setOnClickListener {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    _vib?.vibrate(_vibrationEffect)
                } else {
                    _vib?.vibrate(100)
                }

                val userId = registration_id.text.toString()
                if (userId.isEmpty()){
                    Toast.makeText(this@RegisterUserActivity, "IDを入力してください", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // ダイアログを作成して表示
                AlertDialog.Builder(this@RegisterUserActivity).apply {
                    setMessage("『${userId}』を本当に登録しますか？")
                    setPositiveButton("oK") { it, _ ->
                        it.dismiss()
                        //登録リクエスト非同期通信
                        registerUser()
                    }
                    setNegativeButton("cancel", null)
                    show()
                }
            }
        }
    }

    //ユーザ登録処理
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        //TODO 初期画面表示→徐々に変化
        setContentView(R.layout.init)

        //ALPNプロバイダー生成
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)

        _vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        _vibrationEffect = CommonService().getVibEffect()

        //トークン取得
        _token = CommonService().getToken()

        //トークンの更新イベントレシーバ登録
        val messageFilter = IntentFilter(SingletonService.UPDATE_TOKEN)
        LocalBroadcastManager.getInstance(this).registerReceiver(UpdateTokenReceiver(), messageFilter)

        _dbContext = CommonService().getDbContext(this)

        GlobalScope.launch {
            //ユーザ登録済みかどうか確認
            checkMyInfo()
        }
//        CheckMyInfoAsyncTask().execute()
    }

    override fun onRestart() {
        super.onRestart()
        println("RegisterUserActivity再開")
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        intent.putExtra(SingletonService.SESSION_ID, _sessionId)
        intent.putExtra(SingletonService.STATUS, _status)
        intent.putExtra(SingletonService.USER_ID, _userId)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        println("アプリ終了")
    }

    //メイン画面へ遷移
    private fun moveToMainActivity(userInfo: UserInfo) {
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java).apply {
            putExtra(SingletonService.SESSION_ID, userInfo.sessionId)
            putExtra(SingletonService.STATUS, userInfo.status)
            putExtra(SingletonService.USER_ID, userInfo.userId)
        }
        startActivity(intent)
    }

    //ユーザ登録処理
    private fun registerUser() {
        println("ユーザ登録処理開始")
        _dialog.show(supportFragmentManager, "progress")

        GlobalScope.launch {
            var token = CommonService().getToken()
            if (token.isNullOrEmpty()) {
                token = _token
                if(token.isNullOrEmpty()){
                    token = ""
                }
            }

            val authenChannel = CommonService().getGRPCChannel(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            val agent = AuthenGrpc.newBlockingStub(authenChannel)

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
                displayMessage("サーバに接続できません")
                authenChannel.shutdown()
                return@launch
            }

            if (!response.result){
                _dialog.dismiss()
                authenChannel.shutdown()

                if (response.sessionId == "err") {
                    displayMessage("サーバエラーです")
                    return@launch
                }
                displayMessage("そのIDはすでに登録されています")
                return@launch
            }

            //ユーザ情報を登録
            val user = User().apply {
                userId = _userId!!
                password = response.password
                createdDateTime = CommonService().getNow()
            }

            _dbContext!!.userFactory().insert(user)

            _dialog.dismiss()

            displayMessage("登録が完了しました")

            authenChannel.shutdown()

            //メイン画面へ遷移
            moveToMainActivity(UserInfo(token, _userId, _sessionId, 0))
        }
    }

    private fun displayMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this@RegisterUserActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    //トークンが更新されたことを検知
    inner class UpdateTokenReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("ユーザ登録画面にてトークン取得")

            // Broadcast されたメッセージを取り出す
            _token = intent.getStringExtra(SingletonService.TOKEN)
        }
    }
}