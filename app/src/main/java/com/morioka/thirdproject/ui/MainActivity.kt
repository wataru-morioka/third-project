package com.morioka.thirdproject.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import io.grpc.ManagedChannelBuilder
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.view.ViewPager
import android.widget.Toast
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.adapter.TabsPagerAdapter
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import socket.*
import java.text.SimpleDateFormat
import java.util.*
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import authen.*
import com.morioka.thirdproject.common.SingletonService
import io.grpc.ManagedChannel
import java.lang.Exception
import android.net.NetworkInfo
import android.net.ConnectivityManager
import com.morioka.thirdproject.common.ReceiverService.ConnectionReceiver
import com.morioka.thirdproject.common.ReceiverService.UpdateTokenReceiver

class MainActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, CreateQuestion.OnFragmentInteractionListener,
    MemberStatus.OnFragmentInteractionListener, OthersQuestions.OnFragmentInteractionListener,
    OwnQuestions.OnFragmentInteractionListener, ConnectionReceiver.Observer {
    private var _dbContext: AppDatabase? = null
    private var _sessionId: String? = null
    private var _status: Int = 0
    private var _userId:String? = null
    private var _socketServer: ManagedChannel? = null
    private var _maintenanceSessionFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _dbContext = CommonService().getDbContext(this)

        //トークン更新監視レシーバー登録
        val messageFilter = IntentFilter(SingletonService.UPDATE_TOKEN)
        registerReceiver(UpdateTokenReceiver(_dbContext!!), messageFilter)

        //ネットワーク監視レシーバー登録
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(ConnectionReceiver(this), filter)

        //ユーザ情報取得
        if (savedInstanceState != null) {
            println("MainActivityが復元")

            //サーバとのセッションを破棄
            if (_socketServer != null) {
                _socketServer?.shutdown()
            }
            _sessionId = null

            //初期ログイン画面へ遷移→セッションを再生成
            val intent = Intent(this, RegisterUserActivity::class.java)
            startActivity(intent)

        } else {
            println("MainActivityが生成")
            _sessionId = intent.getStringExtra(SingletonService.SESSION_ID)
            _status = intent.getIntExtra(SingletonService.STATUS,  0)
            _userId = intent.getStringExtra(SingletonService.USER_ID)
        }

        var user = User()

        runBlocking {
            GlobalScope.launch {
                user = _dbContext!!.userFactory().getMyInfo()
                user.status = _status
                _dbContext!!.userFactory().update(user)
            }.join()
        }

        //tabとpagerを紐付ける
        pager.addOnPageChangeListener(this@MainActivity)
        setTabLayout(user, _sessionId)

        //プリバシーポリシーリンク
        privacy_policy_tv.setOnClickListener {
            val uri = Uri.parse("http://wtrmorioka.html.xdomain.jp/policy.html")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        if (!_sessionId.isNullOrEmpty()) {
            _maintenanceSessionFlag = true
        }

        //セッション維持
        GlobalScope.launch {
            maintainSession()
        }
    }

    //オンラインになった時
    override fun onConnect() {
        println("オンライン")
        //ネットワークに接続した時の処理
        if (_socketServer != null && !_sessionId.isNullOrEmpty()) {
            println("セッションが残っている")
            return
        }
        val userInfo = CommonService().login(_dbContext!!)

        if (userInfo?.sessionId.isNullOrEmpty()) {
            return
        }

        _sessionId = userInfo?.sessionId
        _status = userInfo?.status ?: 0
        _userId = userInfo?.userId
        _maintenanceSessionFlag = true

        //サーバとセッション確立
        createSession()
    }

    //オフラインになった時
    override fun onDisconnect() {
        println("オフライン")
        _maintenanceSessionFlag = false
        _socketServer?.shutdown()
        _sessionId = null
    }

    override fun onRestart() {
        super.onRestart()
        println("activity再開")
//        reViewFragment(0)
//        reViewFragment(1)
    }

    override fun onDestroy() {
        super.onDestroy()
        _socketServer?.shutdown()
        println("MainActivityが破棄されました")
    }

//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putString(SingletonService.SESSION_ID, _sessionId)
//        outState.putInt(SingletonService.STATUS, _status)
//    }

    //タブの設定
    private fun setTabLayout(user: User, sessionId: String?) {
        val adapter = TabsPagerAdapter(supportFragmentManager, this, user, sessionId)
        pager.adapter = adapter
        tabs.setupWithViewPager(pager)
//        for (i in 0 until adapter.count) {
//            val tab: TabLayout.Tab = tabs.getTabAt(i)!!
//            tab.customView = adapter.getTabView(tabs, i)
//        }
    }

    //サーバとセッション確立
    private fun createSession() {
        println("サーバとセッション確立開始")

        _socketServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.GRPC_PORT)
            .usePlaintext()
            .build()
        val agent = SocketGrpc.newStub(_socketServer)

        val request = InfoRequest.newBuilder()
            .setSessionId(_sessionId)
            .build()

        var result = false

        agent.getNewInfo(request, object : StreamObserver<InfoResult> {
            override fun onNext(reply: InfoResult) {
                println("res : " + reply.result)
                result = reply.result
                if (!result) {
                    //TODO
                    return
                }

                when (reply.owner) {
                    SingletonService.OWN -> {
                        //自分の質問の集計結果を登録
                        registerAggregationResult(reply, agent)
                    }
                    SingletonService.OTHERS -> {
                        if (reply.determinationFlag) {
                            //他人の質問の集計結果を登録
                            registerAggregationResult(reply, agent)
                            return
                        }
                        //新着他人の質問を登録
                        registerOthersQuestion(reply, agent)
                    }
                    else -> {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "サーバの処理に失敗しました¥nアプリを再起動してください", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onError(t: Throwable?) {
                //TODO
                println("サーバとの接続が切れた、もしくはサーバ側に障害が発生、もしくはクライアントのデータ処理に失敗")
                println(t)
                _socketServer?.shutdown()
            }

            override fun onCompleted() {
                println("サーバからレスポンスを受信")
                if (!result) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "サーバの処理に失敗しました¥nアプリを再起動してください", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    //セッション維持処理
    private fun maintainSession(){
        while (_maintenanceSessionFlag) {
            if (!_maintenanceSessionFlag) {
                Thread.sleep(30000)
                continue
            }

            println("セッション維持処理")

            val authenServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.AUTHEN_PORT)
                .usePlaintext()
                .build()
            val agent = AuthenGrpc.newStub(authenServer)

            val request = MaintenanceRequest.newBuilder()
                .setSessionId(_sessionId)
                .setUserId(_userId)
                .build()

            var result = false

            agent.maintainSession(request, object : StreamObserver<MaintenanceResult> {
                override fun onNext(reply: MaintenanceResult) {
                    println("res : " + reply.result)
                    result = reply.result
                    if (!result) {
                        return
                    }
                }

                override fun onError(t: Throwable?) {
                    //TODO
                    println("セッション維持に失敗しました")
                    authenServer.shutdown()
                }

                override fun onCompleted() {
                    if (!result) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "セッション維持に失敗しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                    authenServer.shutdown()
                }
            })
            Thread.sleep(30000)
        }
    }

    //質問の集計結果を登録
    private fun registerAggregationResult(reply: InfoResult, agent: SocketGrpc.SocketStub){
        println("質問の集計結果を登録")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())

        val question: Question
        when (reply.owner) {
            SingletonService.OWN ->  question = (_dbContext as AppDatabase).questionFactory().getQuestionById(reply.questionId)
            SingletonService.OTHERS -> question = (_dbContext as AppDatabase).questionFactory().getQuestionBySeq(reply.questionSeq)
            else -> return
        }

        question.questionSeq = reply.questionSeq
        question.answer1number = reply.answer1Number
        question.answer2number = reply.answer2Number
        question.timeLimit = reply.timeLimit
        question.confirmationFlag = false
        question.determinationFlag = true
        question.modifiedDateTime = now

        //TODO エラー処理
        try{
            _dbContext!!.questionFactory().update(question)
        } catch(e: Exception) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "データの登録に失敗しました", Toast.LENGTH_SHORT).show()
            }
            return
        }

        //画面を再描画
        runOnUiThread {
            when (reply.owner) {
                SingletonService.OWN -> {
                    println("質問の集計結果更新イベントを周知")
                    onFragmentInteraction(0)

                    // Local Broadcast で発信する（activityも再描画させる）
                    val messageIntent = Intent(SingletonService.OWN)
//                    messageIntent.putExtra("Message", time)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
                }
                SingletonService.OTHERS -> {
                    println("他人の集計結果更新イベントを周知")
                    onFragmentInteraction(1)

                    // Local Broadcast で発信する（activityも再描画させる）
                    val messageIntent = Intent(SingletonService.OTHERS)
//                    messageIntent.putExtra("Message", time)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
                }
                else -> {}
            }
        }

        val request = DoneRequest.newBuilder()
            .setSessionId(_sessionId)
            .setOwner(reply.owner)
            .setQuestionId(reply.questionId)
            .setQuestionSeq(reply.questionSeq)
            .setDeterminationFlag(true)
            .build()

        var result = false

        println("質問の集計結果の登録が完了したことをサーバに送信")
        agent.receiveDone(request, object : StreamObserver<DoneResult> {
            override fun onNext(reply: DoneResult) {
                println("res : " + reply.result)
                result = reply.result
            }

            override fun onError(t: Throwable?) {
            }

            override fun onCompleted() {
                if (!result) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "サーバの処理に失敗しました¥nアプリを再起動してください", Toast.LENGTH_SHORT).show()
                    }
                }
//                socketServer.shutdown()
            }
        })
    }

    //新着他人の質問を登録
    private fun registerOthersQuestion(reply: InfoResult, agent: SocketGrpc.SocketStub){
        println("新着他人の質問を登録")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())

        val count = (_dbContext as AppDatabase).questionFactory().getAlreadyCount(reply.questionSeq)
        if (count != 0) {
            return
        }

        val question = Question()
        question.questionSeq = reply.questionSeq
        question.owner = reply.owner
        question.question = reply.question
        question.answer1 = reply.answer1
        question.answer2 = reply.answer2
        question.targetNumber = reply.targetNumber
        question.timeLimit = reply.timeLimit
        question.createdDateTime = now

        _dbContext!!.questionFactory().insert(question)

        //相手の質問画面を再描画
        runOnUiThread {
            onFragmentInteraction(1)

            // Local Broadcast で発信する（activityも再描画させる）
            val messageIntent = Intent(SingletonService.OTHERS)
//                    messageIntent.putExtra("Message", time)
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
        }

        val request = DoneRequest.newBuilder()
            .setSessionId(_sessionId)
            .setOwner(reply.owner)
            .setQuestionId(reply.questionId)
            .setQuestionSeq(reply.questionSeq)
            .setDeterminationFlag(false)
            .build()

        var result = false

        agent.receiveDone(request, object : StreamObserver<DoneResult> {
            override fun onNext(reply: DoneResult) {
                println("res : " + reply.result)
                result = reply.result
            }

            override fun onError(t: Throwable?) {
            }

            override fun onCompleted() {
                if (!result) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "サーバの処理に失敗しました¥nアプリを再起動してください", Toast.LENGTH_SHORT).show()
                    }
                }
//                socketServer.shutdown()
            }
        })
    }

    //Fragmentの再描画
    private fun reViewFragment(position: Int){
        //アダプターを取得
        val adapter = pager.adapter
        //instantiateItem()で今のFragmentを取得
        val fragment = adapter?.instantiateItem(pager, position) as Fragment

        val transaction = supportFragmentManager.beginTransaction()
        transaction.detach(fragment)
        transaction.attach(fragment)
        transaction.commitAllowingStateLoss()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) {}
    override fun onPageScrollStateChanged(state: Int) {}

    // Fragmentの再描画（コールバック）
    override fun onFragmentInteraction(position: Int) {
        reViewFragment(position)
    }

    // Fragmentからのコールバックメソッド
    override fun onFragmentInteraction(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Fragmentからのコールバックメソッド
    override fun onFragmentInteraction(uri: Uri) {

    }
}
