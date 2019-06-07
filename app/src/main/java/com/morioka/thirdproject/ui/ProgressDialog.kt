package com.morioka.thirdproject.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import com.morioka.thirdproject.R


class ProgressDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context: Context = context!!
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_progress)
        dialog.setCancelable(false)
        dialog.setTitle("更新中")
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    companion object {
        //インスタンス作成
        fun newInstance(): ProgressDialog {
            return ProgressDialog()
        }
    }
} //空のコンストラクタ（DialogFragmentのお約束）