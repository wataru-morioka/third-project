package com.morioka.thirdproject.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import com.squareup.okhttp.ConnectionSpec
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.android.synthetic.main.fragment_member_status.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import socket.SocketGrpc
import socket.StatusResult
import socket.UpdateRequest
import java.lang.Exception


// TODO: Rename parameter arguments, choose names that match
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
    private var _vib: Vibrator? = null
    private var _vibrationEffect: VibrationEffect? = null
    private var _currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            _sessionId = it.getString(SingletonService.SESSION_ID)
            //_status = it.getInt(SingletonService.STATUS)
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
        return inflater.inflate(R.layout.fragment_member_status, container, false)
    }

    // Viewの生成が完了した後に呼ばれる
    // UIパーツの設定などを行う
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        println("onViewCreated")
        super.onViewCreated(view, savedInstanceState)

        userId_tv.text = _userId

        _status = runBlocking{
            GlobalScope.async {
               _dbContext!!.userFactory().getMyInfo().status
            }.await()
        }

        val statusList = CommonService().getStatusData()
        val presentStatus = statusList.find{x -> x.status == _status} as Target
        present_status_tv.text = presentStatus.name

        max_target_spinner.apply {
            adapter = TargetSpinnerAdapter(context!!, statusList)
            setSelection(presentStatus.status)
        }

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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                _vib?.vibrate(_vibrationEffect)
            } else {
                _vib?.vibrate(100)
            }
            val updatedItem = max_target_spinner.selectedItem as Target

            if (updatedItem.status == presentStatus.status){
                Toast.makeText(context, "現在のステータスと同じです", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ダイアログを作成して表示
            AlertDialog.Builder(context).apply {
                setMessage("本当に更新しますか？")
                setPositiveButton("oK") { it, _ ->
                    it.dismiss()
                    //更新処理
                    updateStatus(_sessionId, updatedItem.status)
                }
                setNegativeButton("cancel", null)
                show()
            }
        }
    }

    //ステータス更新処理
    private fun updateStatus(sessionId: String?, status: Int) {
        _dialog.show(fragmentManager, "progress")

        GlobalScope.launch {
            val socketChannel = CommonService().getGRPCChannel(SingletonService.HOST, SingletonService.GRPC_PORT)
            val agent = SocketGrpc.newBlockingStub(socketChannel)

            val request = UpdateRequest.newBuilder()
                .setSessionId(sessionId ?: "")
                .setStatus(status)
                .build()

            val response: StatusResult
            try {
                response = agent.updateStatus(request)
            } catch (e: Exception) {
                _dialog.dismiss()
                displayMessage("サーバに接続できません")
                socketChannel.shutdown()
                return@launch
            }

            if (!response.result) {
                _dialog.dismiss()
                displayMessage("更新に失敗しました")
                socketChannel.shutdown()
                return@launch
            }

            //DB更新
            val user = _dbContext!!.userFactory().getMyInfo()
            user.status = status
            _dbContext!!.userFactory().update(user)

            _listener?.onFragmentInteraction(_currentPosition - 1)
            _listener?.onFragmentInteraction(_currentPosition)

            _dialog.dismiss()

            displayMessage("更新が完了しました")
            socketChannel.shutdown()
        }
    }

    private fun displayMessage(message: String?) {
        activity?.runOnUiThread{
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
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
        fun newInstance(sessionId: String?, user: User, position: Int) =
            MemberStatus().apply {
                arguments = Bundle().apply {
                    putString(SingletonService.SESSION_ID, sessionId)
                    putInt(SingletonService.STATUS, user.status)
                    putString(SingletonService.USER_ID, user.userId)
                    putInt(SingletonService.CURRENT_POSITION, position)
                }
            }
    }
}
