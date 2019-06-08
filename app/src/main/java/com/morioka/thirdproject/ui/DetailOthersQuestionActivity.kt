package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.AdapterView
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.service.AnswerSpinnerAdapter
import com.morioka.thirdproject.service.CommonService
import kotlinx.android.synthetic.main.detail_others_question.*
import kotlinx.android.synthetic.main.detail_others_question.answer1_tv
import kotlinx.android.synthetic.main.detail_others_question.answer2_tv
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DetailOthersQuestionActivity: AppCompatActivity() {
    private val _dialog = ProgressDialog()
    private var _dbContext: AppDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detail_others_question)

        val questionId = intent.getLongExtra("QUESTION_ID", 0)

        _dbContext = CommonService().getDbContext(this)
        var user: User? = null
        var question: Question? = null
        runBlocking {
            GlobalScope.launch {
                user = (_dbContext as AppDatabase).userFactory().getMyInfo()
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

        if (question!!.myDecision != 0) {
            answer_spinner.setSelection(question!!.myDecision - 1)
            answer_spinner.isEnabled = false
            answer_bt.visibility = View.INVISIBLE
            return
        }

        answer_bt.setOnClickListener {
            // ダイアログを作成して表示
            AlertDialog.Builder(this).apply {
                setMessage("本当に送信しますか？")
                setPositiveButton("oK", DialogInterface.OnClickListener { _, _ ->
                    _dialog.show(supportFragmentManager , "test")
                    val decision = answer_spinner.selectedItem as Int

                    //更新処理


                    //再描画



                })
                setNegativeButton("cancel", null)
                show()
            }
        }
    }
}