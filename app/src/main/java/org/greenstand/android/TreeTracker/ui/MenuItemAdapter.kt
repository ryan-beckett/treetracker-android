package org.greenstand.android.TreeTracker.ui

import java.util.ArrayList

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.RelativeLayout
import android.widget.TextView


class MenuItemAdapter(@get:JvmName("getContext_") private val context: Context, resourceId: Int, textViewResourceId: Int,
                      private var objects: List<MenuItem>?) : ArrayAdapter<MenuItem>(context, textViewResourceId, objects) {


    class ViewHolder {
        var item1: TextView? = null
    }


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row: RelativeLayout
        if (convertView == null) {
            val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            row = inflater.inflate(android.R.layout.simple_list_item_1, null) as RelativeLayout
        } else {
            row = convertView as RelativeLayout
        }

        val menuItem: String?
        try {
            menuItem = objects!![position].name
        } catch (e: Exception) {
            return row
        }


        (row.findViewById(android.R.id.text1) as TextView).text = menuItem

        return row
    }


    override fun getFilter(): Filter {
        return object : Filter() {

            override fun publishResults(constraint: CharSequence, results: Filter.FilterResults) {
                objects = results.values as ArrayList<MenuItem>
                notifyDataSetChanged()
                Log.d("objects count", Integer.toString(objects!!.size))
            }

            override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
                val oReturn = Filter.FilterResults()
                val results = ArrayList<MenuItem>()
                val orig = objects as ArrayList<MenuItem>?

                if (orig != null) {
                    if (constraint != null) {
                        if (orig.size > 0) {
                            for (b in orig) {
                                if (b.name!!.toLowerCase().contains(constraint.toString().toLowerCase())) {
                                    results.add(b)
                                }
                            }
                        }

                        oReturn.values = results
                        oReturn.count = results.size
                    }
                }

                return oReturn
            }
        }

    }

}
