package com.morioka.thirdproject.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.morioka.thirdproject.R
import com.morioka.thirdproject.model.Question
import com.morioka.thirdproject.viewholder.OthersQuestionViewHolder

class RecycleOthersQuestionsViewAdapter (private val questionList: List<Question>, private val listener: ListListener) : RecyclerView.Adapter<OthersQuestionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OthersQuestionViewHolder {
        val rowView: View = LayoutInflater.from(parent.context).inflate(R.layout.others_question_list_item, parent, false)
        return OthersQuestionViewHolder(rowView)
    }

    override fun onBindViewHolder(holder: OthersQuestionViewHolder, position: Int) {
        holder.apply {
            //行クリックイベント登録
            itemView.setOnClickListener {
                listener.onClickRow(it, questionList[position])
            }

            //質問セット
            questionTv.text = questionList[position].question

            //未開封のものはアイコンを変える
            if (!questionList[position].confirmationFlag){
                confirmIcon.setImageResource(android.R.drawable.btn_star_big_on)
            } else {
                confirmIcon.setImageResource(android.R.drawable.star_off)
            }

            //集計が完了している場合
            if (questionList[position].determinationFlag) {
                answerIcon.visibility = View.VISIBLE
                answerIcon.setImageResource(android.R.drawable.ic_media_play)
                return
            }

            determinationiTv.visibility = View.INVISIBLE

            //期限内の場合、アイコン表示
            if (questionList[position].myDecision == 0) {
                answerIcon.visibility = View.VISIBLE
                answerIcon.setImageResource(android.R.drawable.ic_menu_edit)
            }
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