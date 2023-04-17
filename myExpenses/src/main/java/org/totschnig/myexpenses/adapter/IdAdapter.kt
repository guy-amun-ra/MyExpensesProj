package org.totschnig.myexpenses.adapter

import android.content.Context
import android.widget.ArrayAdapter
import org.totschnig.myexpenses.R

interface IdHolder {
    val id: Long
}
class  IdAdapter<T: IdHolder>(context: Context, objects: List<T>): ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, android.R.id.text1, objects) {
    constructor(context: Context) : this(context, mutableListOf())

    init {
        setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
    }

    override fun hasStableIds() = true

    override fun getItemId(position: Int) = getItem(position)!!.id

    fun getPosition(accountId: Long): Int {
        for (i in 0 until count) {
            if (getItem(i)!!.id == accountId) return i
        }
        return -1
    }
}