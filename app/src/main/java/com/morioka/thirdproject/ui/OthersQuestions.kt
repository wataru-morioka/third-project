package com.morioka.thirdproject.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.adapter.RecycleOthersQuestionsViewAdapter
import com.morioka.thirdproject.common.SingletonService
import kotlinx.android.synthetic.main.fragment_others_questions.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [OthersQuestions.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [OthersQuestions.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class OthersQuestions : Fragment() {
    // TODO: Rename and change types of parameters
    private var _sessionId: String? = null
    private var _status: Int = 0
    private var _userId: String? = null
    private var _listener: OnFragmentInteractionListener? = null
    private var _dbContext: AppDatabase? = null
    private var _currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _sessionId = it.getString(SingletonService.SESSION_ID)
            _status = it.getInt(SingletonService.STATUS)
            _userId = it.getString(SingletonService.USER_ID)
            _currentPosition = it.getInt(SingletonService.CURRENT_POSITION)
        }

        _dbContext = CommonService().getDbContext(context!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_others_questions, container, false)
    }

    // TODO: Rename method, update argument and hook method into UI event
    fun onButtonPressed(uri: Uri) {
        _listener?.onFragmentInteraction(uri)
    }

    // Viewの生成が完了した後に呼ばれる
    // UIパーツの設定などを行う
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //画面描画
        setScreen()
    }

    //画面描画
    private fun setScreen(){
        var othersQuestionList = listOf<Question>()

        runBlocking {
            GlobalScope.launch {
                othersQuestionList = _dbContext!!.questionFactory().getOthersQuestions(SingletonService.OTHERS)
            }.join()
        }

        val viewAdapter = RecycleOthersQuestionsViewAdapter(
            othersQuestionList,
            object : RecycleOthersQuestionsViewAdapter.ListListener {
                override fun onClickRow(tappedView: View, question: Question) {
                    runBlocking {
                        GlobalScope.launch {
                            //確認フラグ更新
                            val updateQuestion = _dbContext!!.questionFactory().getQuestionById(question.id).apply {
                                confirmationFlag = true
                            }
                            _dbContext!!.questionFactory().update(updateQuestion)
                        }.join()
                    }

                    val intent = Intent(activity, DetailOthersQuestionActivity::class.java).apply {
                        putExtra(SingletonService.QUESTION_ID, question.id)
                    }
                    startActivity(intent)

                    _listener?.onFragmentInteraction(_currentPosition)
                }
            })

        recycle_others_view.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
            adapter = viewAdapter
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

    override fun onResume() {
        super.onResume()

        //画面描画
        setScreen()
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
         * @return A new instance of fragment OthersQuestions.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(sessionId: String?, user: User, position: Int) =
            OthersQuestions().apply {
                arguments = Bundle().apply {
                    putString(SingletonService.SESSION_ID, sessionId)
                    putInt(SingletonService.STATUS, user.status)
                    putString(SingletonService.USER_ID, user.userId)
                    putInt(SingletonService.CURRENT_POSITION, position)
                }
            }
    }
}
