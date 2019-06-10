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
import com.morioka.thirdproject.adapter.RecycleOthersQuestioinsViewAdapter
import com.morioka.thirdproject.common.SingletonService
import kotlinx.android.synthetic.main.fragment_others_questions.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = SingletonService.SESSION_ID
private const val ARG_PARAM2 = SingletonService.STATUS
private const val ARG_PARAM3 = SingletonService.USER_ID

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
    private val _dialog = ProgressDialog()
    private var _dbContext: AppDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _sessionId = it.getString(ARG_PARAM1)
            _status = it.getInt(ARG_PARAM2)
            _userId = it.getString(ARG_PARAM3)
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

        var othersQuestionList = listOf<Question>()

        runBlocking {
            GlobalScope.launch {
                othersQuestionList = (_dbContext as AppDatabase).questionFactory().getOthersQuestions(SingletonService.OTHERS)
            }.join()
        }

        val adapter = RecycleOthersQuestioinsViewAdapter(
            othersQuestionList,
            object : RecycleOthersQuestioinsViewAdapter.ListListener {
                override fun onClickRow(tappedView: View, question: Question) {
                    runBlocking {
                        GlobalScope.launch {
                            //確認フラグ更新
                            val updateQuestion = (_dbContext as AppDatabase).questionFactory().getQuestion(question.id)
                            updateQuestion.confirmationFlag = true
                            (_dbContext as AppDatabase).questionFactory().update(updateQuestion)
                        }.join()
                    }

                    val intent = Intent(activity, DetailOthersQuestionActivity::class.java)
                    intent.putExtra(SingletonService.QUESTION_ID, question.id)
                    startActivity(intent)

                    _listener!!.onFragmentInteraction(1)
                }
            })

        recycle_others_view.setHasFixedSize(true)
        recycle_others_view.layoutManager = LinearLayoutManager(activity)
        recycle_others_view.adapter = adapter
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
         * @return A new instance of fragment OthersQuestions.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(sessionId: String?, user: User) =
            OthersQuestions().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, sessionId)
                    putInt(ARG_PARAM2, user.status)
                    putString(ARG_PARAM3, user.userId)
                }
            }
    }
}
