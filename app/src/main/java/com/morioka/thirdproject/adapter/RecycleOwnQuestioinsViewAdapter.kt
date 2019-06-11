package com.morioka.thirdproject.adapter

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.viewholder.OwnQuestionViewHolder

class RecycleOwnQuestioinsViewAdapter (private val questionList: List<Question>, private val listener: ListListener) : RecyclerView.Adapter<OwnQuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnQuestionViewHolder {
//        Log.d("Adapter", "onCreateViewHolder")
        val rowView: View = LayoutInflater.from(parent.context).inflate(R.layout.own_question_list_item, parent, false)
        return OwnQuestionViewHolder(rowView)
    }

    override fun onBindViewHolder(holder: OwnQuestionViewHolder, position: Int) {
//        Log.d("Adapter", "onBindViewHolder")

//        //質問送信時間セット
//        val sendDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).parse(questionList[position].createdDateTime)
//        val sendDate = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(sendDateTime)
//        holder.send_date_tv.text = sendDate

        //質問セット
        holder.question_tv.text = questionList[position].question

        if (!questionList[position].determinationFlag) {
            holder.determinationi_tv.visibility = View.INVISIBLE
        }

        //未開封のものはアイコンを変える
        if (questionList[position].confirmationFlag == null){
            holder.confirm_icon.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.confirm_icon.setImageResource(android.R.drawable.star_off)
        }

        //期限内の場合、アイコン表示
        if (questionList[position].determinationFlag) {
            holder.aggregator_icon.setImageResource(android.R.drawable.ic_media_play)
        }

        holder.itemView.setOnClickListener {
            listener.onClickRow(it, questionList[position])
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