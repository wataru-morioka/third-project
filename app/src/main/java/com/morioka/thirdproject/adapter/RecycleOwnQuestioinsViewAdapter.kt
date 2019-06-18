package com.morioka.thirdproject.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.viewholder.OwnQuestionViewHolder

class RecycleOwnQuestionsViewAdapter (private val questionList: List<Question>, private val listener: ListListener) : RecyclerView.Adapter<OwnQuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnQuestionViewHolder {
        val rowView: View = LayoutInflater.from(parent.context).inflate(R.layout.own_question_list_item, parent, false)
        return OwnQuestionViewHolder(rowView)
    }

    override fun onBindViewHolder(holder: OwnQuestionViewHolder, position: Int) {
        holder.apply {
            //行クリックイベント登録
            itemView.setOnClickListener {
                listener.onClickRow(it, questionList[position])
            }

            //質問セット
            questionTv.text = questionList[position].question

            if (!questionList[position].determinationFlag) {
                determinationiTv.visibility = View.INVISIBLE
            }

            //未開封のものはアイコンを変える
            if (!questionList[position].confirmationFlag){
                confirmIcon.setImageResource(android.R.drawable.btn_star_big_on)
            } else {
                confirmIcon.setImageResource(android.R.drawable.star_off)
            }

            //期限内の場合、アイコン表示
            if (questionList[position].determinationFlag) {
                aggregatorIcon.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    override fun getItemCount(): Int {
        return questionList.size
    }

    interface ListListener {
        fun onClickRow(tappedView: View, question: Question)
    }
}