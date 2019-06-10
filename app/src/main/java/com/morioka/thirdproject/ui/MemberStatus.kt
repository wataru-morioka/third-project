package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Toast
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.AppDatabase
import com.morioka.thirdproject.model.Target
import com.morioka.thirdproject.model.User
import com.morioka.thirdproject.common.CommonService
import com.morioka.thirdproject.adapter.TargetSpinnerAdapter
import com.morioka.thirdproject.common.SingletonService
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.fragment_member_status.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import socket.SocketGrpc
import socket.StatusResult
import socket.UpdateRequest


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = SingletonService.SESSION_ID
private const val ARG_PARAM2 = SingletonService.STATUS
private const val ARG_PARAM3 = SingletonService.USER_ID

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MemberStatus.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MemberStatus.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class MemberStatus : Fragment() {
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
            //_status = it.getInt(ARG_PARAM2)
            _userId = it.getString(ARG_PARAM3)
        }

        _dbContext = CommonService().getDbContext(context!!)
        println("onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        println("onCreateView")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_member_status, container, false)
    }

    // Viewの生成が完了した後に呼ばれる
    // UIパーツの設定などを行う
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        println("onViewCreated")
        super.onViewCreated(view, savedInstanceState)

        userId_tv.text = _userId

        runBlocking{
            GlobalScope.launch {
                _status = (_dbContext as AppDatabase).userFactory().getMyInfo().status
            }.join()
        }

        val statusList = CommonService().getStatusData()
        val presentStatus = statusList.find{x -> x.status == _status} as Target
        present_status_tv.text = presentStatus.name

        val context: Context = context!!
        val adapter = TargetSpinnerAdapter(context, statusList)
        max_target_spinner.adapter = adapter
        max_target_spinner.setSelection(presentStatus.status)

        // リスナーを登録
        max_target_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            // アイテムが選択された時
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = (parent as Spinner).selectedItem as Target
                selected_status_tv.text = item.name
            }

            // アイテムが選択されなかった
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        //「送信」ボタンクリックイベント
        update_status_bt.setOnClickListener {

            val updatedItem = max_target_spinner.selectedItem as Target

            if (updatedItem.status == presentStatus.status){
                Toast.makeText(context, "現在のステータスと同じです", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ダイアログを作成して表示
            AlertDialog.Builder(context).apply {
                setMessage("本当に更新しますか？")
                setPositiveButton("oK", DialogInterface.OnClickListener { _, _ ->
                    _dialog.show(fragmentManager, "test")
                    //更新処理
                    updateStatus(_sessionId as String, updatedItem.status)
                })
                setNegativeButton("cancel", null)
                show()
            }
        }
    }

    //ステータス更新処理
    private fun updateStatus(sessionId: String, status: Int){
        val socketServer = ManagedChannelBuilder.forAddress(SingletonService.HOST, SingletonService.GRPC_PORT)
            .usePlaintext()
            .build()
        val agent = SocketGrpc.newStub(socketServer)

        val request = UpdateRequest.newBuilder()
            .setSessionId(sessionId)
            .setStatus(status)
            .build()

        var result = false

        agent.updateStatus(request, object : StreamObserver<StatusResult> {
            override fun onNext(reply: StatusResult) {
                println("受信成功：" + reply.result)
                result = reply.result
            }

            override fun onError(t: Throwable?) {
                _dialog.dismiss()
                activity?.runOnUiThread{
                    Toast.makeText(activity, "更新に失敗しました", Toast.LENGTH_SHORT).show()
                }
                socketServer.shutdown()
            }

            override fun onCompleted() {
                if (!result) {
                    _dialog.dismiss()
                    activity?.runOnUiThread{
                        Toast.makeText(activity, "更新に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                    socketServer.shutdown()
                    return
                }

                //DB更新
                val user = (_dbContext as AppDatabase).userFactory().getMyInfo()
                user.status = status
                (_dbContext as AppDatabase).userFactory().update(user)

                activity?.runOnUiThread{
                    Toast.makeText(activity, "更新が完了しました", Toast.LENGTH_SHORT).show()
                }

                _listener?.onFragmentInteraction(2)
                _listener?.onFragmentInteraction(3)

                _dialog.dismiss()
                socketServer.shutdown()
            }
        })
    }

//    // TODO: Rename method, update argument and hook method into UI event
//    fun onButtonPressed(uri: Uri) {
//        _listener?.onFragmentInteraction()
//    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            _listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
        println("Attach")
    }

    override fun onDetach() {
        super.onDetach()
        _listener = null
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
        fun onFragmentInteraction(message: String)
        fun onFragmentInteraction(position: Int)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MemberStatus.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(sessionId: String?, user: User) =
            MemberStatus().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, sessionId)
                    putInt(ARG_PARAM2, user.status)
                    putString(ARG_PARAM3, user.userId)
                }
            }
    }
}
