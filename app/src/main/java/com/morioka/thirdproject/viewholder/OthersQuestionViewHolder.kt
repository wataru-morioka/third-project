package com.morioka.thirdproject.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.others_question_list_item.view.*
import kotlinx.android.synthetic.main.others_question_list_item.view.confirm_icon

class OthersQuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    val confirm_icon = itemView.confirm_icon
    val question_tv = itemView.others_question_tv
    val answer_icon = itemView.answer_icon
    val determinationi_tv = itemView.determination_tv
}
