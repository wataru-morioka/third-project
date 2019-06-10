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
import com.google.gson.Gson
import com.morioka.thirdproject.common.SingletonService
import com.morioka.thirdproject.model.*
import com.rabbitmq.client.ConnectionFactory

private const val QUEUE_NAME = SingletonService.ANSWER

class DetailOthersQuestionActivity: AppCompatActivity() {
    private val _dialog = ProgressDialog()
    private var _dbContext: AppDatabase? = null
    private var _questionId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detail_others_question)

        if (savedInstanceState != null) {
            _questionId = savedInstanceState.getLong(SingletonService.QUESTION_ID)
        } else {
            _questionId = intent.getLongExtra(SingletonService.QUESTION_ID, 0)
        }

        //画面描画
        setScreen(_questionId)

        //イベント検知レシーバーー登録
        val messageFilter = IntentFilter(SingletonService.OTHERS)
        // LocalBroadcast の設定
        LocalBroadcastManager.getInstance(this).registerReceiver(UpdateInfoReceiver(_questionId), messageFilter)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SingletonService.QUESTION_ID, _questionId)
    }

    private fun setScreen(questionId: Long) {
        _dbContext = CommonService().getDbContext(this)
        var question: Question? = null
        runBlocking {
            GlobalScope.launch {
                question = (_dbContext as AppDatabase).questionFactory().getQuestion(questionId)
            }.join()
        }

        others_question_tv.text = question!!.question
        answer1_tv.text = question!!.answer1
        answer2_tv.text = question!!.answer2

        val adapter = AnswerSpinnerAdapter(this, listOf(1, 2))

        answer_spinner.adapter = adapter
        // リスナーを登録
        answer_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            // アイテムが選択された時
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                val item = (parent as Spinner).selectedItem as Target
//                Toast.makeText(context, item.name, Toast.LENGTH_SHORT).show()
            }

            // アイテムが選択されなかった
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //質問送信時間セット
        val receiveDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).parse(question!!.createdDateTime)
        val sendDate = SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN).format(receiveDateTime)
        receive_date_tv.text = sendDate

        time_limit_tv.text = question!!.timeLimit

        //集計結果受信前
        if (question!!.determinationFlag) {
            answer1_number_tv.visibility = View.VISIBLE
            answer2_number_tv.visibility = View.VISIBLE
            answer1_percentage_tv.visibility = View.VISIBLE
            answer2_percentage_tv.visibility = View.VISIBLE

            answer1_number_tv.text = getString(R.string.answer_number, question!!.answer1number)
            answer2_number_tv.text= getString(R.string.answer_number, question!!.answer2number)

            val answer1percentage = question!!.answer1number / (question!!.answer1number + question!!.answer2number)
            val answer2percentage = 100 - answer1percentage

            answer1_percentage_tv.text = getString(R.string.answer_percentage, answer1percentage)
            answer2_percentage_tv.text = getString(R.string.answer_percentage, answer2percentage)
        }

        if (question!!.myDecision != 0) {
            answer_spinner.setSelection(question!!.myDecision - 1)
            answer_spinner.isEnabled = false
            answer_bt.visibility = View.INVISIBLE
            return
        }

        //TODO
//        val now = Date()
//        val timeLimit = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).parse(question!!.timeLimit)
//
//        if (now > timeLimit) {
//            your_choice_tv.visibility = View.INVISIBLE
//            answer_spinner.visibility = View.INVISIBLE
//            answer_bt.visibility = View.INVISIBLE
//            finished_tv.visibility = View.VISIBLE
//            return
//        }

        answer_bt.setOnClickListener {
//            if (now > timeLimit) {
//                Toast.makeText(this@DetailOthersQuestionActivity, "時間切れです", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
            // ダイアログを作成して表示
            AlertDialog.Builder(this).apply {
                setMessage("本当に送信しますか？")
                setPositiveButton("oK", DialogInterface.OnClickListener { _, _ ->
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
        _dialog.show(supportFragmentManager , "test")

        runBlocking {
            GlobalScope.launch {
                val question = (_dbContext as AppDatabase).questionFactory().getQuestion(questionId)
                val user = (_dbContext as AppDatabase).userFactory().getMyInfo()

                //データベース更新
                question.myDecision = decision
                question.modifiedDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())
                (_dbContext as AppDatabase).questionFactory().update(question)

                val factory = ConnectionFactory()
                factory.host = "10.0.2.2"
                val connection = factory.newConnection()
                val channel = connection.createChannel()

                //メッセージ作成
                val answerRequest = AnswerRequest(
                    question.questionSeq,
                    user.userId,
                    decision,
                    question.timeLimit!!
                )

                //クラスオベジェクトをJSON文字列にデシリアライズ
                val message = Gson().toJson(answerRequest)

                channel.queueDeclare(QUEUE_NAME, false, false, false, null)
                try {
                    channel.txSelect()

                    channel.basicPublish("", QUEUE_NAME, null, message.toByteArray(charset("UTF-8")))

                    channel.txCommit()

                    println("キューメッセージ送信に成功しました")
                    println(" [x] Sent '$message'")
                    runOnUiThread{
                        _dialog.dismiss()
                        Toast.makeText(this@DetailOthersQuestionActivity, "キューメッセージ送信に成功しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    println("キューメッセージ送信に失敗しました")

                    channel.txRollback()

                    //データベース更新
                    question.myDecision = 0
                    (_dbContext as AppDatabase).questionFactory().update(question)

                    runOnUiThread{
                        Toast.makeText(this@DetailOthersQuestionActivity, "キューメッセージ送信に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    _dialog.dismiss()

                    channel.close()
                    connection.close()
                }
            }.join()
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