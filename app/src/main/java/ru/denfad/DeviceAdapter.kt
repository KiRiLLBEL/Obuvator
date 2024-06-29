package ru.denfad.bluetoothwriterreader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class DeviceAdapter(private val context: Context, private val devices: List<String>) : BaseAdapter() {

    private val connectedDevices = mutableListOf<String>()

    override fun getCount(): Int {
        return devices.size
    }

    override fun getItem(position: Int): String {
        return devices[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)
        val deviceName = getItem(position)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = deviceName

        if (connectedDevices.contains(deviceName)) {
            view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
        } else {
            view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        }

        return view
    }

    fun updateConnectedDevices(connectedDevices: List<String>) {
        this.connectedDevices.clear()
        this.connectedDevices.addAll(connectedDevices)
        notifyDataSetChanged()
    }
}
