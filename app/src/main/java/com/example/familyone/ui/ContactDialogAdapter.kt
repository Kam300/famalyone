package com.example.familyone.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.familyone.R

data class ContactOption(
    val title: String,
    val iconRes: Int
)

class ContactDialogAdapter(
    context: Context,
    private val options: List<ContactOption>
) : ArrayAdapter<ContactOption>(context, 0, options) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.dialog_contact_item, parent, false)
        
        val option = options[position]
        
        view.findViewById<ImageView>(R.id.ivIcon).setImageResource(option.iconRes)
        view.findViewById<TextView>(R.id.tvTitle).text = option.title
        
        return view
    }
}
