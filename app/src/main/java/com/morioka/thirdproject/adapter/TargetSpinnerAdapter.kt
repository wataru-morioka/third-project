package com.morioka.thirdproject.adapter

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.morioka.thirdproject.model.Target


class TargetSpinnerAdapter : ArrayAdapter<Target> {
    constructor(context: Context) : super(context, android.R.layout.simple_spinner_item) {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    constructor(context: Context, list: ArrayList<Target>) : super(
        context,
        android.R.layout.simple_spinner_item,
        list
    ) {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getView(position, convertView, parent) as TextView
        textView.setText(getItem(position)!!.targetNumber.toString())
        textView.setTextColor(Color.parseColor("#F2F2F2"))
        return textView
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getDropDownView(position, convertView, parent) as TextView
        textView.setText(getItem(position)!!.targetNumber.toString())
        return textView
    }
}