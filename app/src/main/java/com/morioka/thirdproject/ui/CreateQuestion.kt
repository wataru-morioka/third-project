package com.morioka.thirdproject.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat.getSystemService
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.adapter.TargetSpinnerAdapter
import kotlinx.android.synthetic.main.fragment_create_question.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.widget.*
import com.google.gson.Gson
import com.morioka.thirdproject.common.SingletonService
import com.morioka.thirdproject.model.*
import com.morioka.thirdproject.model.Target
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

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
    private var _vib: Vibrator? = null
    private var _vibrationEffect: VibrationEffect? = null
    private var _currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _sessionId = it.getString(SingletonService.SESSION_ID)
            _userId = it.getString(SingletonService.USER_ID)
            _currentPosition = it.getInt(SingletonService.CURRENT_POSITION)
        }

        _dbContext = CommonService().getDbContext(context!!)

        _vib = activity?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        _vibrationEffect = CommonService().getVibEffect()
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

        _status = runBlocking {
            GlobalScope.async {
                _dbContext!!.userFactory().getMyInfo().status
            }.await()
        }

        val targetList = CommonService().getStatusData().filter{ x -> x.status <= _status} as ArrayList<Target>
        target_spinner.adapter = TargetSpinnerAdapter(context!!, targetList)

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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                _vib?.vibrate(_vibrationEffect)
            } else {
                _vib?.vibrate(100)
            }

            if (question_tv.isEnabled || answer1_tv.isEnabled || answer2_tv.isEnabled || target_spinner.isEnabled) {
                displayErrorMessage("確定していない項目があります")
                return@setOnClickListener
            }

            //質問をキューサーバに送信
            askMyQuestionin()
        }
    }

    //質問をキューサーバに送信
    private fun askMyQuestionin(){
        _dialog.show(fragmentManager , "progress")

        val selectedTarget = target_spinner.selectedItem as Target

        GlobalScope.launch {
            _dbContext!!.run {
                //トランザクション開始
                beginTransaction()

                //DBに登録し、その際のquestionIdを取得
                val questionId = registerQuestion(selectedTarget.targetNumber)

                val connection: Connection?
                val channel: Channel?

                try {
                    val factory = CommonService().getFactory()
                    connection = factory.newConnection()
                    channel = connection.createChannel()
                    channel.queueDeclare(SingletonService.QUESTION, true, false, false, null)
                } catch (e: Exception) {
                    println("エラー：キューサーバとの接続に失敗")
                    endTransaction()
                    _dialog.dismiss()
                    activity?.runOnUiThread{
                        Toast.makeText(context!!, "サーバに接続できません", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                //メッセージ作成
                val questionRequest = QuestionRequest(
                    _userId ?: "",
                    questionId,
                    question_tv.text.toString(),
                    answer1_tv.text.toString(),
                    answer2_tv.text.toString(),
                    selectedTarget.targetNumber,
                    SingletonService.TIME_PERIOD
                )

                //クラスオベジェクトをJSON文字列にデシリアライズ
                val message = Gson().toJson(questionRequest)

                try {
                    channel.run {
                        channel.txSelect()
                        channel.basicPublish("", SingletonService.QUESTION, null, message.toByteArray(charset("UTF-8")))
                        channel.txCommit()
                    }

                    println("メッセージ送信に成功しました")
                    println(" [x] Sent '$message'")
                    _listener?.onFragmentInteraction(_currentPosition)

                    //コミット
                    setTransactionSuccessful()

                    //画面をクリア
                    activity?.runOnUiThread{
                        //TODO リセット処理
                        question_tv.setText("")
                        answer1_tv.setText("")
                        answer2_tv.setText("")
                        Toast.makeText(activity, "送信が完了しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    println("メッセージ送信に失敗しました")

                    channel.txRollback()

                    val deleteQuestion = questionFactory().getQuestionById(questionId)
                    questionFactory().delete(deleteQuestion)

                    //画面をクリア
                    activity?.runOnUiThread{
                        Toast.makeText(activity, "メッセージ送信に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    endTransaction()
                    channel.close()
                    connection.close()
                    _dialog.dismiss()
                }
            }
        }
    }

    //DBに登録し、その際のquestionIdを取得
    private fun registerQuestion(targetNum: Int): Long{
        val question = Question().apply {
            owner = SingletonService.OWN
            question = question_tv.text.toString()
            answer1 = answer1_tv.text.toString()
            answer2 = answer2_tv.text.toString()
            targetNumber = targetNum
            timePeriod = SingletonService.TIME_PERIOD
            createdDateTime = CommonService().getNow()
        }

        //TODO エラー処理
        return _dbContext!!.questionFactory().insert(question)
    }

    private fun displayErrorMessage(message: String){
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun changeTextStyle(target_tv: TextView, target_bt: Button){
        if (target_tv.length() == 0){
            displayErrorMessage("入力してください")
            return
        }

        if (target_tv.isEnabled) {
            target_bt.apply {
                setBackgroundResource(R.drawable.edit_button_style)
                text = "編集"
            }
            target_tv.isEnabled = false
        } else {
            target_bt.apply {
                setBackgroundResource(R.drawable.confirm_button_style)
                text = "確定"
            }
            target_tv.isEnabled = true
        }
    }

    private fun changeSpinnerStyle(target_spinner: Spinner, target_bt: Button){
        if (target_spinner.isEnabled){
            target_bt.apply {
                setBackgroundResource(R.drawable.edit_button_style)
                text = "編集"
            }
            target_spinner.isEnabled = false
        } else {
            target_bt.apply {
                setBackgroundResource(R.drawable.confirm_button_style)
                text = "確定"
            }
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
        fun newInstance(sessionId: String?, user: User, position: Int) =
            CreateQuestion().apply {
                arguments = Bundle().apply {
                    putString(SingletonService.SESSION_ID, sessionId)
                    putInt(SingletonService.STATUS, user.status)
                    putString(SingletonService.USER_ID, user.userId)
                    putInt(SingletonService.CURRENT_POSITION, position)
                }
            }
    }
}
