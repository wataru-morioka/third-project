package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import com.morioka.thirdproject.R
import com.morioka.thirdproject.adapter.AnswerSpinnerAdapter
import com.morioka.thirdproject.common.CommonService
import kotlinx.android.synthetic.main.detail_others_question.*
import kotlinx.android.synthetic.main.detail_others_question.answer1_number_tv
import kotlinx.android.synthetic.main.detail_others_question.answer1_percentage_tv
import kotlinx.android.synthetic.main.detail_others_question.answer1_tv
import kotlinx.android.synthetic.main.detail_others_question.answer2_number_tv
import kotlinx.android.synthetic.main.detail_others_question.answer2_percentage_tv
import kotlinx.android.synthetic.main.detail_others_question.answer2_tv
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.support.v4.content.LocalBroadcastManager
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v4.content.ContextCompat
import com.google.gson.Gson
import com.morioka.thirdproject.common.SingletonService
import com.morioka.thirdproject.model.*
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.async

class DetailOthersQuestionActivity: AppCompatActivity() {
    private val _dialog = ProgressDialog()
    private var _dbContext: AppDatabase? = null
    private var _questionId: Long = 0
    private var _vib: Vibrator? = null
    private var _vibrationEffect: VibrationEffect? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detail_others_question)

        _vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
        _vibrationEffect = CommonService().getVibEffect()

        _questionId = getQuestionId(savedInstanceState)

        _dbContext = CommonService().getDbContext(this)

        //データ更新イベントレシーバ登録
        val messageFilter = IntentFilter(SingletonService.OTHERS)
        LocalBroadcastManager.getInstance(this).registerReceiver(UpdateInfoReceiver(_questionId), messageFilter)

        //画面描画
        setScreen(_questionId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SingletonService.QUESTION_ID, _questionId)
    }

    private fun getQuestionId(savedInstanceState: Bundle?): Long {
        return when (savedInstanceState) {
            null -> intent.getLongExtra(SingletonService.QUESTION_ID, 0)
            else -> savedInstanceState.getLong(SingletonService.QUESTION_ID)
        }
    }

    private fun setScreen(questionId: Long) {
        val question = runBlocking {
            GlobalScope.async {
                _dbContext!!.questionFactory().getQuestionById(questionId)
            }.await()
        }

        others_question_tv.text = question.question
        answer1_tv.text = question.answer1
        answer2_tv.text = question.answer2

        val adapter = AnswerSpinnerAdapter(this, listOf(1, 2))

        answer_spinner.adapter = adapter

        //質問送信時間セット
        receive_date_tv.text = CommonService().changeDateFormat(question.createdDateTime)

        time_limit_tv.text = question.timeLimit

        //集計結果受信前
        if (question.determinationFlag) {
            answer1_number_tv.visibility = View.VISIBLE
            answer2_number_tv.visibility = View.VISIBLE
            answer1_percentage_tv.visibility = View.VISIBLE
            answer2_percentage_tv.visibility = View.VISIBLE

            answer1_number_tv.text = getString(R.string.answer_number, question!!.answer1number)
            answer2_number_tv.text= getString(R.string.answer_number, question!!.answer2number)

            when (question.answer1number + question?.answer2number) {
                0 -> {
                    answer1_percentage_tv.text = "0%"
                    answer2_percentage_tv.text = "0%"
                }
                else -> {
                    val answer1percentage = question.answer1number * 100 / (question.answer1number + question.answer2number)
                    val answer2percentage = 100 - answer1percentage

                    answer1_percentage_tv.text = getString(R.string.answer_percentage, answer1percentage)
                    answer2_percentage_tv.text = getString(R.string.answer_percentage, answer2percentage)
                }
            }
        }

        if (question.myDecision != 0) {
            answer_spinner.apply {
                setSelection(question.myDecision - 1)
                isEnabled = false
            }
            answer_bt.visibility = View.INVISIBLE
            return
        }

        //TODO
        val now = Date()
        val timeLimit = CommonService().getDateTime(question.timeLimit!!)

        if (now > timeLimit) {
            answer1_number_tv.visibility = View.INVISIBLE
            answer2_number_tv.visibility = View.INVISIBLE
            answer1_percentage_tv.visibility = View.INVISIBLE
            answer2_percentage_tv.visibility = View.INVISIBLE
            extend_tv.visibility = View.INVISIBLE
            your_choice_tv.visibility = View.INVISIBLE
            answer_spinner.visibility = View.INVISIBLE
            answer_bt.visibility = View.INVISIBLE
            finished_tv.visibility = View.VISIBLE
            return
        }

        answer_bt.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                _vib?.vibrate(_vibrationEffect)
            } else {
                _vib?.vibrate(100)
            }
            if (now > timeLimit) {
                Toast.makeText(this@DetailOthersQuestionActivity, "時間切れです", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // ダイアログを作成して表示
            AlertDialog.Builder(this).apply {
                setMessage("本当に送信しますか？")
                setPositiveButton("oK", DialogInterface.OnClickListener { alertDialog, _ ->
                    alertDialog.dismiss()
                    val decision = answer_spinner.selectedItem as Int

                    //回答送信処理
                    submitMyDecision(decision, questionId)

                    //再描画
                    setScreen(questionId)
                })
                setNegativeButton("cancel", null)
                show()
            }
        }
    }

    //回答送信処理
    private fun submitMyDecision(decision: Int, questionId: Long){
        _dialog.show(supportFragmentManager , "progress")

        GlobalScope.launch {
            _dbContext!!.run {
                val question = questionFactory().getQuestionById(questionId).apply {
                    //データベース更新
                    myDecision = decision
                    modifiedDateTime = CommonService().getNow()
                }
                val user = _dbContext!!.userFactory().getMyInfo()

                //トランザクション開始
                beginTransaction()

                questionFactory().update(question)

                val connection: Connection?
                val channel: Channel?

                try {
                    val factory = CommonService().getFactory()
                    connection = factory.newConnection()
                    channel = connection.createChannel()
                    channel.queueDeclare(SingletonService.ANSWER, true, false, false, null)
                } catch (e: Exception) {
                    println("サーバとの接続に失敗")
                    endTransaction()
                    _dialog.dismiss()
                    displayMessage("サーバとの接続に失敗しました")
                    return@launch
                }

                //メッセージ作成
                val answerRequest = AnswerRequest(
                    question.questionSeq,
                    user.userId,
                    decision,
                    question.timeLimit ?: ""
                )

                //クラスオベジェクトをJSON文字列にデシリアライズ
                val message = Gson().toJson(answerRequest)

                try {
                    channel.run {
                        txSelect()
                        basicPublish("", SingletonService.ANSWER, null, message.toByteArray(charset("UTF-8")))
                        txCommit()
                    }

                    //コミット
                    setTransactionSuccessful()

                    println("キューメッセージ送信に成功しました")
                    println(" [x] Sent '$message'")
                    displayMessage("回答送信に成功しました")
                } catch (e: Exception) {
                    println("キューメッセージ送信に失敗しました")

                    channel.txRollback()

                    displayMessage("回答送信に失敗しました")
                } finally {
                    endTransaction()
                    channel.close()
                    connection.close()
                    _dialog.dismiss()
                }
            }

        }
    }

    private fun displayMessage(message: String) {
        runOnUiThread{
            Toast.makeText(this@DetailOthersQuestionActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    //データ更新通知を検知
    inner class UpdateInfoReceiver(private val questionId: Long)  : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DataReceiver", "onReceive")

//            Broadcast されたメッセージを取り出す
//            val message = intent.getStringExtra("Message")
            // 画面再描画
            setScreen(questionId)
        }
    }
}