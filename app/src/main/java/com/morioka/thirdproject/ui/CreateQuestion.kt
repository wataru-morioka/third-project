package com.morioka.thirdproject.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.service.CommonService
import com.morioka.thirdproject.service.TargetSpinnerAdapter
import kotlinx.android.synthetic.main.fragment_create_question.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.widget.*
import com.google.gson.Gson
import com.morioka.thirdproject.model.*
import com.morioka.thirdproject.model.Target
import com.rabbitmq.client.ConnectionFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "SESSION_ID"
private const val ARG_PARAM2 = "STATUS"
private const val ARG_PARAM3 = "USER_ID"
private const val QUEUE_NAME = "question"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [CreateQuestion.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [CreateQuestion.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class CreateQuestion : Fragment() {
    // TODO: Rename and change types of parameters
    private var _sessionId: String? = null
    private var _userId: String? = null
    private var _status: Int = 0
    private var _listener: OnFragmentInteractionListener? = null
    private val _dialog = ProgressDialog()
    private var _dbContext: AppDatabase? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _sessionId = it.getString(ARG_PARAM1)
            _userId = it.getString(ARG_PARAM3)
        }

        _dbContext = CommonService().getDbContext(context!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_create_question, container, false)
    }

    // TODO: Rename method, update argument and hook method into UI event
    fun onButtonPressed(uri: Uri) {
        _listener?.onFragmentInteraction(uri)
    }

    // Viewの生成が完了した後に呼ばれる
    // UIパーツの設定などを行う
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runBlocking {
            GlobalScope.launch {
                _status = (_dbContext as AppDatabase).userFactory().getMyInfo().status
            }.join()
        }

        val targetList = CommonService().getStatusData().filter{x -> x.status <= _status} as ArrayList<Target>

        val adapter = TargetSpinnerAdapter(context!!, targetList)
        target_spinner.adapter = adapter
        // リスナーを登録
        target_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            // アイテムが選択された時
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                val item = (parent as Spinner).selectedItem as Target
//                Toast.makeText(context, item.name, Toast.LENGTH_SHORT).show()
            }

            // アイテムが選択されなかった
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        question_confirm_bt.setOnClickListener {
            changeTextStyle(question_tv, question_confirm_bt)
        }

        answer1_confirm_bt.setOnClickListener {
            changeTextStyle(answer1_tv, answer1_confirm_bt)
        }

        answer2_confirm_bt.setOnClickListener {
            changeTextStyle(answer2_tv, answer2_confirm_bt)
        }

        target_confirm_bt.setOnClickListener {
            changeSpinnerStyle(target_spinner, target_confirm_bt)
        }

        //TODO 一度送信した後規定時間まで無効
        //「送信」ボタンクリックイベント
        ask_bt.setOnClickListener {
            if (question_tv.isEnabled || answer1_tv.isEnabled || answer2_tv.isEnabled || target_spinner.isEnabled) {
                displayErrorMessage("確定していない項目があります")
                return@setOnClickListener
            }

            _dialog.show(fragmentManager , "test")

            //質問をキューサーバに送信
            askMyQuestionin()
        }
    }

    //質問をキューサーバに送信
    private fun askMyQuestionin(){
        val selectedTarget = target_spinner.selectedItem as Target

        runBlocking {
            GlobalScope.launch {
                //DBに登録し、その際のquestionIdを取得
                val questionId = registerQuestion(selectedTarget.targetNumber)

                val factory = ConnectionFactory()
                factory.host = "10.0.2.2"
                //TODO エラー処理
                val connection = factory.newConnection()
                val channel = connection.createChannel()

                //メッセージ作成
                val questionRequest = QuestionRequest(
                    _userId!!,
                    questionId,
                    question_tv.text.toString(),
                    answer1_tv.text.toString(),
                    answer2_tv.text.toString(),
                    selectedTarget.targetNumber,
                    5
                )

                //クラスオベジェクトをJSON文字列にデシリアライズ
                val message = Gson().toJson(questionRequest)

                channel.queueDeclare(QUEUE_NAME, false, false, false, null)
                try {
                    channel.txSelect()

                    channel.basicPublish("", QUEUE_NAME, null, message.toByteArray(charset("UTF-8")))

                    channel.txCommit()

                    println("キューメッセージ送信に成功しました")
                    println(" [x] Sent '$message'")
                    _listener?.onFragmentInteraction(2)

                    //画面をクリア
                    activity!!.runOnUiThread{
                        //TODO リセット処理
                        question_tv.setText("")
                        answer1_tv.setText("")
                        answer2_tv.setText("")
                        Toast.makeText(activity, "送信が完了しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    println("キューメッセージ送信に失敗しました")

                    channel.txRollback()

                    val deleteQuestion = (_dbContext as AppDatabase).questionFactory().getQuestion(questionId)
                    (_dbContext as AppDatabase).questionFactory().delete(deleteQuestion)

                    //画面をクリア
                    activity!!.runOnUiThread{
                        Toast.makeText(activity, "キューメッセージ送信に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    _dialog.dismiss()

                    channel.close()
                    connection.close()
                }
            }.join()
        }
    }

    //DBに登録し、その際のquestionIdを取得
    private fun registerQuestion(targetNumber: Int): Long{
        val question = Question()
        question.owner = "own"
        question.question = question_tv.text.toString()
        question.answer1 = answer1_tv.text.toString()
        question.answer2 = answer2_tv.text.toString()
        question.targetNumber = targetNumber
        question.timePeriod = 5
        question.createdDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(Date())

        //TODO エラー処理
        return (_dbContext as AppDatabase).questionFactory().insert(question)
    }

    private fun displayErrorMessage(message: String){
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun changeTextStyle(target_tv: TextView, target_bt: Button){
        if (target_tv.length() == 0){
            displayErrorMessage("入力してください")
            return
        }

        if (target_tv.isEnabled){
            target_bt.setBackgroundResource(R.drawable.edit_button_style)
            target_bt.text = "編集"
            target_tv.isEnabled = false
        }else{
            target_bt.setBackgroundResource(R.drawable.confirm_button_style)
            target_bt.text = "確定"
            target_tv.isEnabled = true
        }
    }

    private fun changeSpinnerStyle(target_spinner: Spinner, target_bt: Button){
        if (target_spinner.isEnabled){
            target_bt.setBackgroundResource(R.drawable.edit_button_style)
            target_bt.text = "編集"
            target_spinner.isEnabled = false
        }else{
            target_bt.setBackgroundResource(R.drawable.confirm_button_style)
            target_bt.text = "確定"
            target_spinner.isEnabled = true
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            _listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        _listener = null
    }

    override fun onStart() {
        super.onStart()

        println("onStart")


    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
        fun onFragmentInteraction(position: Int)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CreateQuestion.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(sessionId: String?, user: User) =
            CreateQuestion().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, sessionId)
                    putInt(ARG_PARAM2, user.status)
                    putString(ARG_PARAM3, user.userId)
                }
            }
    }
}