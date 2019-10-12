package com.morioka.thirdproject.adapter

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class AnswerSpinnerAdapter : ArrayAdapter<Int> {
    constructor(context: Context) : super(context, android.R.layout.simple_spinner_item) {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    constructor(context: Context, list: List<Int>) : super(
        context,
        android.R.layout.simple_spinner_item,
        list
    ) {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getView(position, convertView, parent) as TextView
        return textView.apply {
            text = getItem(position)?.toString()
            setTextColor(Color.parseColor("#F2F2F2"))
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getDropDownView(position, convertView, parent) as TextView
        return textView.apply { text = getItem(position)?.toString() }
    }
}