package com.morioka.thirdproject.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.common.SingletonService
import kotlinx.android.synthetic.main.detail_own_question.*
import kotlinx.android.synthetic.main.detail_own_question.answer1_number_tv
import kotlinx.android.synthetic.main.detail_own_question.answer1_percentage_tv
import kotlinx.android.synthetic.main.detail_own_question.answer1_tv
import kotlinx.android.synthetic.main.detail_own_question.answer2_number_tv
import kotlinx.android.synthetic.main.detail_own_question.answer2_percentage_tv
import kotlinx.android.synthetic.main.detail_own_question.answer2_tv
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

class DetailOwnQuestionActivity: AppCompatActivity() {
    private var _dbContext: AppDatabase? = null
    private var _questionId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detail_own_question)

        if (savedInstanceState != null) {
            _questionId = savedInstanceState.getLong(SingletonService.QUESTION_ID)
        } else {
            _questionId = intent.getLongExtra(SingletonService.QUESTION_ID, 0)
        }

        //画面描画
        setScreen(_questionId)

        val messageFilter = IntentFilter(SingletonService.OWN)
        // Broadcast を受け取る BroadcastReceiver を設定
        // LocalBroadcast の設定
        LocalBroadcastManager.getInstance(this).registerReceiver(UpdateInfoReceiver(_questionId), messageFilter)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SingletonService.QUESTION_ID, _questionId)
    }

    //画面描画
    private fun setScreen(questionId: Long){
        _dbContext = CommonService().getDbContext(this)
        var user: User? = null
        var question: Question? = null
        runBlocking {
            GlobalScope.launch {
                user = (_dbContext as AppDatabase).userFactory().getMyInfo()
                question = (_dbContext as AppDatabase).questionFactory().getQuestionById(questionId)
            }.join()
        }

        own_question_tv.text = question!!.question
        answer1_tv.text = question!!.answer1
        answer2_tv.text = question!!.answer2

        //質問送信時間セット
        val sendDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).parse(question!!.createdDateTime)
        val sendDate = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(sendDateTime)
        send_date_tv.text = sendDate

        target_number_tv.text = getString(R.string.target_number, question!!.targetNumber)
        time_period_tv.text = getString(R.string.time_period, question!!.timePeriod)

        //集計結果受信前
        if (!question!!.determinationFlag) {
            // Date型の日時をCalendar型に変換
            val calendar = Calendar.getInstance()
            calendar.time = sendDateTime

            // 日時を加算する
            calendar.add(Calendar.MINUTE, question!!.timePeriod)

            // Calendar型の日時をDate型に戻す
            val timeLimit = calendar.time

            if (Date() > timeLimit) {
                aggregator_tv.text = "集計処理待ち"
            }
            return
        }

        //集計結果受信後
        aggregator_tv.visibility = View.INVISIBLE
        answer1_number_tv.visibility = View.VISIBLE
        answer2_number_tv.visibility = View.VISIBLE
        answer1_percentage_tv.visibility = View.VISIBLE
        answer2_percentage_tv.visibility = View.VISIBLE

        answer1_number_tv.text = getString(R.string.answer_number, question!!.answer1number)
        answer2_number_tv.text= getString(R.string.answer_number, question!!.answer2number)

        when (question!!.answer1number + question!!.answer2number) {
            0 -> {
                answer1_percentage_tv.text = "0%"
                answer2_percentage_tv.text = "0%"
            }
            else -> {
                val answer1percentage = question!!.answer1number * 100 / (question!!.answer1number + question!!.answer2number)
                val answer2percentage = 100 - answer1percentage

                answer1_percentage_tv.text = getString(R.string.answer_percentage, answer1percentage)
                answer2_percentage_tv.text = getString(R.string.answer_percentage, answer2percentage)
            }
        }
    }

    //データ更新通知を検知
    inner class UpdateInfoReceiver(private val questionId: Long)  : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DataReceiver", "onReceive")

//            // Broadcast されたメッセージを取り出す
//            val message = intent.getStringExtra("Message")
            // 画面再描画
            setScreen(questionId)
        }
    }

}