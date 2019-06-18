package com.morioka.thirdproject.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.others_question_list_item.view.*
import kotlinx.android.synthetic.main.others_question_list_item.view.confirm_icon

class OthersQuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    val confirmIcon: ImageView = itemView.confirm_icon
    val questionTv: TextView = itemView.others_question_tv
    val answerIcon: ImageView = itemView.answer_icon
    val determinationiTv: TextView = itemView.determination_tv
}
