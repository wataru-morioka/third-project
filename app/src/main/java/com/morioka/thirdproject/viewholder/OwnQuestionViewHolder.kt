package com.morioka.thirdproject.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.own_question_list_item.view.*
import kotlinx.android.synthetic.main.own_question_list_item.view.own_question_tv

class OwnQuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    val confirm_icon = itemView.confirm_icon
    val question_tv = itemView.own_question_tv
    val aggregator_icon = itemView.aggregator_icon
}