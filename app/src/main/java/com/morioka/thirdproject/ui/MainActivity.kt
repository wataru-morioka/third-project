package com.morioka.thirdproject.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import io.grpc.ManagedChannelBuilder
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.view.ViewPager
import android.widget.Toast
import authen.LoginRequest
import authen.LoginResult
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.service.CommonService
import com.morioka.thirdproject.service.TabsPagerAdapter
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.register_user.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.android.synthetic.main.activity_main.view.*
import socket.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, CreateQuestion.OnFragmentInteractionListener,
    MemberStatus.OnFragmentInteractionListener, OthersQuestions.OnFragmentInteractionListener,
    OwnQuestions.OnFragmentInteractionListener {
    private var _dbContext: AppDatabase? = null
    private var _sessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //ユーザ情報取得
        //val userId = intent.getStringExtra("USER_ID")
        val sessionId = intent.getStringExtra("SESSION_ID")
        _sessionId = sessionId
        val status = intent.getIntExtra("STATUS", -1)
        var user = User()

        _dbContext = CommonService().getDbContext(this)

        runBlocking {
            GlobalScope.launch {
                user = (_dbContext as AppDatabase).userFactory().getMyInfo()
                user.status = status
                (_dbContext as AppDatabase).userFactory().update(user)
            }.join()
        }

        println("サーバとセッション確立")
        //サーバとセッション確立
        createSession()

        //tabとpagerを紐付ける
        pager.addOnPageChangeListener(this@MainActivity)
        setTabLayout(user, sessionId)
//
//        while (true) {
//            Thread.sleep(2000)
//            println("確認")
//        }
    }

    override fun onRestart() {
        super.onRestart()
        println("activity再開")

    }



    //タブの設定
    private fun setTabLayout(user: User, sessioniId: String) {
        val adapter = TabsPagerAdapter(supportFragmentManager, this, user, sessioniId)
        pager.adapter = adapter
        tabs.setupWithViewPager(pager)
//        for (i in 0 until adapter.count) {
//            val tab: TabLayout.Tab = tabs.getTabAt(i)!!
//            tab.customView = adapter.getTabView(tabs, i)
//        }
    }

    //サーバとセッション確立
    private fun createSession() {
        val socketServer = ManagedChannelBuilder.forAddress("10.0.2.2", 50050)
            .usePlaintext()
            .build()
        val agent = SocketGrpc.newStub(socketServer)

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
                    "own" -> {
                        //自分の質問の集計結果を登録
                        registerAggregationResult(reply, agent)
                    }
                    "others" -> {
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
                println("サーバとセッション確立に失敗しました")
                socketServer.shutdown()
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

    //質問の集計結果を登録
    private fun registerAggregationResult(reply: InfoResult, agent: SocketGrpc.SocketStub){
        println("質問の集計結果を登録")
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())

        val question: Question
        when (reply.owner) {
            "own" ->  question = (_dbContext as AppDatabase).questionFactory().getQuestion(reply.questionId)
            "others" ->  question = (_dbContext as AppDatabase).questionFactory().getQuestion(reply.questionSeq)
            else -> return
        }

        question.questionSeq = reply.questionSeq
        question.answer1number = reply.answer1Number
        question.answer2number = reply.answer2Number
        question.timeLimit = reply.timeLimit
        question.determinationFlag = true
        question.modifiedDateTime = now

        (_dbContext as AppDatabase).questionFactory().update(question)

        //画面を再描画
        runOnUiThread {
            when (reply.owner) {
                "own" ->  onFragmentInteraction(0)
                "others" ->  onFragmentInteraction(1)
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

        val count = (_dbContext as AppDatabase).questionFactory().getSameCount(reply.questionSeq)
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

        (_dbContext as AppDatabase).questionFactory().insert(question)

        //相手の質問画面を再描画
        runOnUiThread {
            onFragmentInteraction(1)
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

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) {}
    override fun onPageScrollStateChanged(state: Int) {}

    // Fragmentの再描画（コールバック）
    override fun onFragmentInteraction(position: Int) {
        //アダプターを取得
        val adapter = pager.adapter
        //instantiateItem()で今のFragmentを取得
        val fragment = adapter?.instantiateItem(pager, position) as Fragment

        val transaction = supportFragmentManager.beginTransaction()
        transaction.detach(fragment)
        transaction.attach(fragment)
        transaction.commit()
    }



    // Fragmentからのコールバックメソッド
    override fun onFragmentInteraction(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Fragmentからのコールバックメソッド
    override fun onFragmentInteraction(uri: Uri) {

    }
}
