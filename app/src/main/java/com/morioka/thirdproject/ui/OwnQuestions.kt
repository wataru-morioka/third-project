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
import com.morioka.thirdproject.service.CommonService
import com.morioka.thirdproject.service.RecycleOthersQuestioinsViewAdapter
import com.morioka.thirdproject.service.RecycleOwnQuestioinsViewAdapter
import kotlinx.android.synthetic.main.fragment_own_questions.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "SESSION_ID"
private const val ARG_PARAM2 = "STATUS"
private const val ARG_PARAM3 = "USER_ID"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [BlankFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [BlankFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class OwnQuestions : Fragment() {
    // TODO: Rename and change types of parameters
    private var _sessionId: String? = null
    private var _status: Int = 0
    private var _userId: String? = null
    private var _listener: OnFragmentInteractionListener? = null
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
        return inflater.inflate(R.layout.fragment_own_questions, container, false)
    }

    // Viewの生成が完了した後に呼ばれる
    // UIパーツの設定などを行う
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var othersQuestionList = listOf<Question>()

        runBlocking {
            GlobalScope.launch {
                othersQuestionList = (_dbContext as AppDatabase).questionFactory().getOthersQuestions("own")
            }.join()
        }

        val adapter = RecycleOwnQuestioinsViewAdapter(othersQuestionList, object : RecycleOwnQuestioinsViewAdapter.ListListener {
            override fun onClickRow(tappedView: View, question: Question) {
                //集計結果を受信していた場合、確認フラグ更新
                if (question.determinationFlag) {
                    runBlocking {
                        GlobalScope.launch {
                            //確認フラグ更新
                            val updateQuestion = (_dbContext as AppDatabase).questionFactory().getQuestion(question.id)
                            updateQuestion.confirmationFlag = true
                            (_dbContext as AppDatabase).questionFactory().update(updateQuestion)
                        }.join()
                    }
                }
                val intent = Intent(activity, DetailOwnQuestionActivity::class.java)
                intent.putExtra("QUESTION_ID", question.id)
                startActivity(intent)

                _listener!!.onFragmentInteraction(0)
            }
        })

        recycle_own_view.setHasFixedSize(true)
        recycle_own_view.layoutManager = LinearLayoutManager(activity)
        recycle_own_view.adapter = adapter
    }

    // TODO: Rename method, update argument and hook method into UI event
    fun onButtonPressed(uri: Uri) {
        _listener?.onFragmentInteraction(uri)
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
         * @return A new instance of fragment BlankFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(sessionId: String?, user: User) =
            OwnQuestions().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, sessionId)
                    putInt(ARG_PARAM2, user.status)
                    putString(ARG_PARAM3, user.userId)
                }
            }
    }
}
