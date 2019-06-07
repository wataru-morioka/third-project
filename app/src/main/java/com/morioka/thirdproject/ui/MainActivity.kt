package com.morioka.thirdproject.ui

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.Fragment
import io.grpc.ManagedChannelBuilder
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.view.ViewPager
import android.widget.Toast
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.service.CommonService
import com.morioka.thirdproject.service.TabsPagerAdapter
import kotlinx.android.synthetic.main.register_user.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import socket.SocketGrpc
import kotlinx.android.synthetic.main.activity_main.view.*


class MainActivity : AppCompatActivity(), ViewPager.OnPageChangeListener, CreateQuestion.OnFragmentInteractionListener,
    MemberStatus.OnFragmentInteractionListener, OthersQuestions.OnFragmentInteractionListener,
    OwnQuestions.OnFragmentInteractionListener {
    private var _dbContext: AppDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //ユーザ情報取得
        //val userId = intent.getStringExtra("USER_ID")
        val sessionId = intent.getStringExtra("SESSION_ID")
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

        //サーバとセッション確立
        createSession(sessionId)

        //tabとpagerを紐付ける
        pager.addOnPageChangeListener(this@MainActivity)
        setTabLayout(user, sessionId)
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

    //サーバとセッション確立
    private fun createSession(sessionId: String) {
        val socketServer = ManagedChannelBuilder.forAddress("10.0.2.2", 50050)
                                                .usePlaintext()
                                                .build()
        val agent = SocketGrpc.newStub(socketServer)

        socketServer.shutdown()




    }
//
//    class Sender{
//        companion object {
//            const val QUEUE_NAME = "first"
//        }
//
//        fun hello(){
////            val factory = ConnectionFactory()
////            factory.host = "10.0.2.2"
////            val connection = factory.newConnection()
////            val channel = connection.createChannel()
////
////            channel.queueDeclare(Sender.QUEUE_NAME, false, false, false, null)
////            val message = "Hello World!"
////            channel.basicPublish("", Sender.QUEUE_NAME, null, message.toByteArray(charset("UTF-8")))
////            println(" [x] Sent '$message'")
////
////            channel.close()
////            connection.close()
//
//            val localhost = ManagedChannelBuilder.forAddress("10.0.2.2", 50050)
//                .usePlaintext()
//                .build()
//            val greeter = HelloGrpcGrpc.newStub(localhost)
//
//            val request = SendRequest.newBuilder()
//                .setId("first")
//                .setName("asakura")
//                .setContent("success!")
//                .build()
//
//            val request2 = RoomRequest.newBuilder()
//                .setId("first")
//                .build()
//
////            greeter.addRoom(request2, object : StreamObserver<RoomInfo> {
////                override fun onNext(reply: RoomInfo) {
////                    println("complete")
////                    //println("res : " + reply.result)
////                }
////
////                override fun onError(t: Throwable?) {}
////
////                override fun onCompleted() {
////                    println("complete")
////                }
////            })
//
//
//            val observer = greeter.sendMessage(object : StreamObserver<SendResult> {
//                override fun onNext(reply: SendResult) {
//                    println("res : " + reply.result)
//                }
//
//                override fun onError(t: Throwable?) {}
//
//                override fun onCompleted() {
//                    println("complete")
//                }
//            })
//
//            println("成功")
//
//            observer.onNext(request)
//            observer.onCompleted()
//        }
//    }
}
