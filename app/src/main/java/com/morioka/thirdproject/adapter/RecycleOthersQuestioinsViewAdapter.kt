package com.morioka.thirdproject.adapter

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.viewholder.OthersQuestionViewHolder
import java.text.SimpleDateFormat
import java.util.*

class RecycleOthersQuestioinsViewAdapter (private val questionList: List<Question>, private val listener: ListListener) : RecyclerView.Adapter<OthersQuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OthersQuestionViewHolder {
//        Log.d("Adapter", "onCreateViewHolder")
        val rowView: View = LayoutInflater.from(parent.context).inflate(R.layout.others_question_list_item, parent, false)
        return OthersQuestionViewHolder(rowView)
    }

    override fun onBindViewHolder(holder: OthersQuestionViewHolder, position: Int) {
//        Log.d("Adapter", "onBindViewHolder")

        //質問セット
        holder.question_tv.text = questionList[position].question

        //未開封のものはアイコンを変える
        if (questionList[position].confirmationFlag == null){
            holder.confirm_icon.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.confirm_icon.setImageResource(android.R.drawable.star_off)
        }

        holder.itemView.setOnClickListener {
            listener.onClickRow(it, questionList[position])
        }

        if (questionList[position].determinationFlag) {
            holder.answer_icon.visibility = View.VISIBLE
            holder.answer_icon.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        holder.determinationi_tv.visibility = View.INVISIBLE

        //期限内の場合、アイコン表示
        if (questionList[position].myDecision == 0) {
            holder.answer_icon.visibility = View.VISIBLE
            holder.answer_icon.setImageResource(android.R.drawable.ic_menu_edit)
        }
    }

    override fun getItemCount(): Int {
//        Log.d("Adapter", "getItemCount")
//        println("質問数：" + questionList.size)
        return questionList.size
    }

    interface ListListener {
        fun onClickRow(tappedView: View, question: Question)
    }
}