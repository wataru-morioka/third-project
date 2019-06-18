package com.morioka.thirdproject.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.android.synthetic.main.own_question_list_item.view.*
import kotlinx.android.synthetic.main.own_question_list_item.view.own_question_tv

class OwnQuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
    val confirmIcon: ImageView = itemView.confirm_icon
    val questionTv: TextView = itemView.own_question_tv
    val aggregatorIcon: ImageView = itemView.aggregator_icon
    val determinationiTv: TextView = itemView.determination_tv
}