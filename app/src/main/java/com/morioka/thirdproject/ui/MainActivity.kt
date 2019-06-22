package com.morioka.thirdproject.ui

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
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
import android.support.v4.content.LocalBroadcastManager
import authen.*
import com.morioka.thirdproject.common.SingletonService
import io.grpc.ManagedChannel
import java.lang.Exception
import android.net.ConnectivityManager
import com.morioka.thirdproject.common.ReceiverService.ConnectionReceiver
import com.morioka.thirdproject.common.ReceiverService.UpdateTokenReceiver
import io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory
import io.grpc.netty.shaded.io.netty.util.internal.logging.JdkLoggerFactory
import org.conscrypt.Conscrypt
import java.security.Security

class MainActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, CreateQuestion.OnFragmentInteractionListener,
    MemberStatus.OnFragmentInteractionListener, OthersQuestions.OnFragmentInteractionListener,
    OwnQuestions.OnFragmentInteractionListener, ConnectionReceiver.Observer {
    private var _dbContext: AppDatabase? = null
    private var _sessionId: String? = null
    private var _status: Int = 0
    private var _userId:String? = null
    private var _socketChannel: ManagedChannel? = null
    private var _maintenanceSessionFlag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)

        _dbContext = CommonService().getDbContext(this)
        val token = CommonService().getToken()

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
            if (_socketChannel != null) {
                _socketChannel?.shutdown()
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

        //ユーザステータスをサーバに合わせる
        runBlocking {
            GlobalScope.launch {
                _dbContext!!.run {
                    user = userFactory().getMyInfo()
                    user.status = _status
                    userFactory().update(user)
                }
            }.join()
        }

        //tabとpagerを紐付ける
        setTabLayout(user, _sessionId)

        //プリバシーポリシーリンク
        privacy_policy_tv.setOnClickListener {
            val uri = Uri.parse(SingletonService.PRIVACY_POLICY_URL)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        if (!_sessionId.isNullOrEmpty()) {
            _maintenanceSessionFlag = true
            //サーバとセッション確立
            createSession()
        }

        //セッション維持
        GlobalScope.launch {
            maintainSession(user.userId)
        }
    }

    //オンラインになった時
    override fun onConnect() {
        println("オンライン")
        //ネットワークに接続した時の処理
        if (!_sessionId.isNullOrEmpty()) {
            println("セッションが残っている")
            return
        }

        //ログイン
        val userInfo = CommonService(this@MainActivity).login(_dbContext!!)

        if (_socketChannel == null && userInfo.sessionId.isNullOrEmpty()) {
            return
        }

        _sessionId = userInfo.sessionId
        _status = userInfo.status
        _userId = userInfo.userId
        _maintenanceSessionFlag = true

        //サーバとセッション確立
        createSession()
    }

    //オフラインになった時
    override fun onDisconnect() {
        println("オフライン")
        _maintenanceSessionFlag = false
        _socketChannel?.shutdown()
        _sessionId = null
    }

    override fun onRestart() {
        super.onRestart()
        println("activity再開")
    }

    override fun onDestroy() {
        super.onDestroy()
        _socketChannel?.shutdown()
        println("MainActivityが破棄されました")
    }

//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        outState.putString(SingletonService.SESSION_ID, _sessionId)
//        outState.putInt(SingletonService.STATUS, _status)
//    }

    //タブの設定
    private fun setTabLayout(user: User, sessionId: String?) {
        pager.apply {
            addOnPageChangeListener(this@MainActivity)
            adapter = TabsPagerAdapter(supportFragmentManager, user, sessionId)
        }
        tabs.setupWithViewPager(pager)
//        for (i in 0 until adapter.count) {
//            val tab: TabLayout.Tab = tabs.getTabAt(i)!!
//            tab.customView = adapter.getTabView(tabs, i)
//        }
    }

    //サーバとセッション確立
    private fun createSession() {
        println("サーバとセッション確立開始")

        _socketChannel = CommonService().getGRPCChannel(SingletonService.HOST, SingletonService.GRPC_PORT)
        val agent = SocketGrpc.newStub(_socketChannel)

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
                        displayMessage("サーバの処理に失敗しました¥nアプリを再起動してください")
                    }
                }
            }

            override fun onError(t: Throwable?) {
                //TODO
                println("サーバとの接続が切れた、もしくはサーバ側に障害が発生、もしくはクライアントのデータ処理に失敗")
                println(t)
                _socketChannel?.shutdown()
            }

            override fun onCompleted() {
                println("サーバからレスポンスを受信")
                if (!result) {
                    displayMessage("サーバの処理に失敗しました¥nアプリを再起動してください")
                }
            }
        })
    }

    private fun displayMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    //セッション維持処理
    private fun maintainSession(userId: String){
        while (_maintenanceSessionFlag) {
            if (!_maintenanceSessionFlag) {
                Thread.sleep(30000)
                continue
            }

            println("セッション維持処理")

            val authenServer = CommonService().getGRPCChannel(SingletonService.HOST, SingletonService.AUTHEN_PORT)
            val agent = AuthenGrpc.newStub(authenServer)

            val request = MaintenanceRequest.newBuilder()
                .setSessionId(_sessionId)
                .setUserId(userId)
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
                        displayMessage("セッション維持に失敗しました")
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

        val question = when (reply.owner) {
            SingletonService.OWN ->  _dbContext?.questionFactory()?.getQuestionById(reply.questionId)
            SingletonService.OTHERS ->  _dbContext?.questionFactory()?.getQuestionBySeq(reply.questionSeq)
            else -> return
        }

        question?: return

        question.apply {
            questionSeq = reply.questionSeq
            answer1number = reply.answer1Number
            answer2number = reply.answer2Number
            timeLimit = reply.timeLimit
            confirmationFlag = false
            determinationFlag = true
            modifiedDateTime = CommonService().getNow()
        }


        //TODO エラー処理
        try{
            _dbContext!!.questionFactory().update(question)
        } catch(e: Exception) {
            displayMessage("データの登録に失敗しました")
            return
        }

        //画面を再描画
        runOnUiThread {
            when (reply.owner) {
                SingletonService.OWN -> {
                    println("質問の集計結果更新イベントを周知")
                    onFragmentInteraction(0)

                    //自分の質問の集計結果更新イベントを周知
                    val messageIntent = Intent(SingletonService.OWN)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent)
                }
                SingletonService.OTHERS -> {
                    println("他人の集計結果更新イベントを周知")
                    onFragmentInteraction(1)

                    //他人の集計結果更新イベントを周知
                    val messageIntent = Intent(SingletonService.OTHERS)
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
                    displayMessage("サーバの処理に失敗しました¥nアプリを再起動してください")
                }
            }
        })
    }

    //新着他人の質問を登録
    private fun registerOthersQuestion(reply: InfoResult, agent: SocketGrpc.SocketStub){
        println("新着他人の質問を登録")
        val count = _dbContext!!.questionFactory().getAlreadyCount(reply.questionSeq)
        if (count != 0) {
            return
        }

        val question = Question().apply {
            questionSeq = reply.questionSeq
            owner = reply.owner
            question = reply.question
            answer1 = reply.answer1
            answer2 = reply.answer2
            targetNumber = reply.targetNumber
            timeLimit = reply.timeLimit
            createdDateTime = CommonService().getNow()
        }

        _dbContext!!.questionFactory().insert(question)

        runOnUiThread {
            //相手の質問画面を再描画
            onFragmentInteraction(1)
            //他人の質問を受信したころを周知
            val messageIntent = Intent(SingletonService.OTHERS)
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
                    displayMessage("サーバの処理に失敗しました¥nアプリを再起動してください")
                }
            }
        })
    }

    //Fragmentの再描画
    private fun reViewFragment(position: Int){
        //アダプターを取得
        val adapter = pager.adapter
        val fragment = adapter?.instantiateItem(pager, position) as Fragment

        supportFragmentManager.beginTransaction().run {
            detach(fragment)
            attach(fragment)
            commitAllowingStateLoss()
        }
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
