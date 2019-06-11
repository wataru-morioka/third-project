package com.morioka.thirdproject.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
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
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.register_user.*
import java.text.SimpleDateFormat
import java.util.*

class RegisterUserActivity : AppCompatActivity() {
    private var _sessionId: String? = null
    private var _token: String? = null
    private var _dbContext: AppDatabase? = null
    private  var _status: Int = 0

    //初期画面条件分岐
    inner class CheckMyInfoAsyncTask : AsyncTask<Void, Int, Boolean>() {
        override fun onPreExecute() {
        }

        override fun doInBackground(vararg param: Void?): Boolean {
            val count = (_dbContext as AppDatabase).userFactory().getCount()

            //新規ユーザだった場合、登録画面表示
            if (count == 0) {
                return false
            }

            //ユーザ登録済みだった場合、サーバにセッションをもらいメイン画面へ遷移
            login()
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
                //登録リクエスト非同期通信
                registerUser()
            }
        }
    }

    //ユーザ登録処理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //トークン取得
        getToken()
        if (_token == null) {
            _token = ""
        }

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

    //トークン取得
    private fun getToken() {
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                println("トークン取得に失敗しました")
                Log.w("FIREBASE", "getInstanceId failed", task.exception)
                return@addOnCompleteListener
            }

            println("トークン取得")
            Log.i("FIREBASE", "[CALLBACK] Token = ${task.result?.token}")
            _token = task.result?.token
        }
    }

    override fun onRestart() {
        super.onRestart()
        println("RegisterUserActivity再開")
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        //intent.putExtra("USER_ID", userId)
        intent.putExtra(SingletonService.SESSION_ID, _sessionId)
        intent.putExtra(SingletonService.STATUS, _status)
        startActivity(intent)
    }


    override fun onDestroy() {
        super.onDestroy()

        //TODO アプリをアンインストールした際の処理

        //セッションクリア
        CommonService().logout(_sessionId as String)
        println("アプリ終了")
    }


    //ログイン処理
    private fun login() {
        println("ログイン処理開始")

        val user = (_dbContext as AppDatabase).userFactory().getMyInfo()

        val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            .usePlaintext()
            .build()
        val agent = AuthenGrpc.newStub(authenServer)

        val request = LoginRequest.newBuilder()
            .setUserId(user.userId)
            .setPassword(user.password)
            .setToken(_token)
            .build()

        var result = false

        agent.login(request, object : StreamObserver<LoginResult> {
            override fun onNext(reply: LoginResult) {
                println("res : " + reply.sessionId + reply.status)
                result = reply.result
                _sessionId = reply.sessionId
                _status = reply.status
            }

            override fun onError(t: Throwable?) {
                authenServer.shutdown()
            }

            override fun onCompleted() {
                if (!result) {
                    runOnUiThread {
                        Toast.makeText(this@RegisterUserActivity, "無効なアカウントです", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
                //intent.putExtra("USER_ID", user.userId)
                intent.putExtra(SingletonService.SESSION_ID, _sessionId)
                intent.putExtra(SingletonService.STATUS, _status)
                intent.putExtra(SingletonService.USER_ID, user.userId)
                startActivity(intent)

                authenServer.shutdown()
            }
        })
    }

    //ユーザ登録処理
    private fun registerUser() {
        println("ユーザ登録処理開始")
        //TODO 暗号化
        val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            .usePlaintext()
            .build()

        val agent = AuthenGrpc.newStub(authenServer)

        val userId = registration_id.text.toString()
        if (userId.isEmpty()){
            runOnUiThread {
                Toast.makeText(this@RegisterUserActivity, "IDを入力してください", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val request = RegistrationRequest.newBuilder()
            .setUserId(userId)
            .setToken(_token)
            .build()

        var result = false
        var password = ""

        //サーバに非同期通信（ユーザ登録依頼）
        agent.register(request, object : StreamObserver<RegistrationResult> {
            override fun onNext(reply: RegistrationResult) {
                println("res : " + reply.result + reply.password + reply.sessionId)
                result = reply.result
                //TODO パスワード暗号化
                password = reply.password
                _sessionId = reply.sessionId
            }

            override fun onError(t: Throwable?) {
                authenServer.shutdown()
            }

            override fun onCompleted() {
                if (!result){
                    runOnUiThread {
                        Toast.makeText(this@RegisterUserActivity, "そのIDはすでに登録されています", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                //ユーザ情報を登録
                val user = User()
                user.userId = userId
                user.password = password
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())
                user.createdDateTime = now
                (_dbContext as AppDatabase).userFactory().insert(user)

                runOnUiThread {
                    Toast.makeText(this@RegisterUserActivity, "登録が完了いたしました", Toast.LENGTH_SHORT).show()
                }

                val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
                //intent.putExtra("USER_ID", userId)
                intent.putExtra(SingletonService.SESSION_ID, _sessionId)
                intent.putExtra(SingletonService.STATUS, 0)
                intent.putExtra(SingletonService.USER_ID, userId)
                startActivity(intent)

                authenServer.shutdown()
            }
        })
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