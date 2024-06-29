package ru.denfad.bluetoothwriterreader

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ConnectionViewModel : ViewModel() {

    private val _connectedDevices = MutableLiveData<MutableList<BluetoothDevice>>().apply { value = mutableListOf() }
    val connectedDevices: LiveData<MutableList<BluetoothDevice>> get() = _connectedDevices

    private val _bluetoothSocketMap = MutableLiveData<MutableMap<String, BluetoothSocket>>().apply { value = mutableMapOf() }
    val bluetoothSocketMap: LiveData<MutableMap<String, BluetoothSocket>> get() = _bluetoothSocketMap

    fun addDevice(device: BluetoothDevice, socket: BluetoothSocket) {
        _connectedDevices.value?.apply {
            add(device)
            _connectedDevices.value = this
        }
        _bluetoothSocketMap.value?.apply {
            put(device.address, socket)
            _bluetoothSocketMap.value = this
        }
    }

    fun removeDevice(device: BluetoothDevice) {
        _connectedDevices.value?.apply {
            remove(device)
            _connectedDevices.value = this
        }
        _bluetoothSocketMap.value?.apply {
            remove(device.address)
            _bluetoothSocketMap.value = this
        }
    }

    fun getSocket(device: BluetoothDevice): BluetoothSocket? {
        return _bluetoothSocketMap.value?.get(device.address)
    }
}
