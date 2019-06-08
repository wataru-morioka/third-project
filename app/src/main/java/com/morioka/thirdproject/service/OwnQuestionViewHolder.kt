package com.morioka.thirdproject.service

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.others_question_list_item.view.*
import kotlinx.android.synthetic.main.others_question_list_item.view.aggregater_icon
import kotlinx.android.synthetic.main.others_question_list_item.view.confirm_icon
import kotlinx.android.synthetic.main.own_question_list_item.view.*

class OwnQuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    val confirm_icon = itemView.confirm_icon
    val question_tv = itemView.own_question_tv
    val aggregater_icon = itemView.aggregater_icon
}