package com.morioka.thirdproject.ui

import android.arch.persistence.room.Room
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.iid.FirebaseInstanceId
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.service.CommonService
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.register_user.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class RegisterUserActivity : AppCompatActivity() {
    private var _sessionId: String? = null
    private var _token: String? = null
    private var _dbContext: AppDatabase? = null

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

        val messageFilter = IntentFilter("update_token")
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
        println("activity再開")
        val intent = Intent(this@RegisterUserActivity, MainActivity::class.java)
        //intent.putExtra("USER_ID", userId)
        intent.putExtra("SESSION_ID", _sessionId)
        intent.putExtra("STATUS", 0)
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
        println(_token!!.length)

        val user = (_dbContext as AppDatabase).userFactory().getMyInfo()

        val authenServer = ManagedChannelBuilder.forAddress("10.0.2.2", 50030)
            .usePlaintext()
            .build()
        val agent = AuthenGrpc.newStub(authenServer)

        val request = LoginRequest.newBuilder()
            .setUserId(user.userId)
            .setPassword(user.password)
            .setToken(_token)
            .build()

        var result = false
        var sessionId: String? = null
        var status = 0

        agent.login(request, object : StreamObserver<LoginResult> {
            override fun onNext(reply: LoginResult) {
                println("res : " + reply.sessionId + reply.status)
                result = reply.result
                sessionId = reply.sessionId
                _sessionId = reply.sessionId
                status = reply.status
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
                intent.putExtra("SESSION_ID",sessionId)
                intent.putExtra("STATUS", status)
                startActivity(intent)

                authenServer.shutdown()
            }
        })
    }

    //ユーザ登録処理
    private fun registerUser() {
        println("ユーザ登録処理開始")
        //TODO 暗号化
        val authenServer = ManagedChannelBuilder.forAddress("10.0.2.2", 50030)
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
        var sessionId: String? = null

        //サーバに非同期通信（ユーザ登録依頼）
        agent.register(request, object : StreamObserver<RegistrationResult> {
            override fun onNext(reply: RegistrationResult) {
                println("res : " + reply.result + reply.password + reply.sessionId)
                result = reply.result
                //TODO パスワード暗号化
                password = reply.password
                sessionId = reply.sessionId
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
                intent.putExtra("SESSION_ID", sessionId)
                intent.putExtra("STATUS", 0)
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
            _token = intent.getStringExtra("TOKEN")
        }
    }
}