package ru.denfad.bluetoothwriterreader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import ru.denfad.bluetoothwriterreader.databinding.ActivityBluetoothConnectionBinding
import java.io.IOException
import java.util.*

class BluetoothConnectionActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var binding: ActivityBluetoothConnectionBinding
    private lateinit var adapter: ArrayAdapter<String>
    private val bondedDevicesNames = mutableListOf<String>()
    private val connectedDevices = mutableListOf<BluetoothDevice>()

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        checkAndRequestPermissions()

        populateBondedDevices()

        binding.saveButton.setOnClickListener {
            savePairedDevices()
        }

        binding.devicesListView.setOnItemClickListener { parent, view, position, id ->
            val deviceName = bondedDevicesNames[position]
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionRequiredMessage()
                return@setOnItemClickListener
            }
            val device = bluetoothAdapter?.bondedDevices?.find { it.name == deviceName }
            device?.let {
                if (connectedDevices.contains(it)) {
                    disconnectFromDevice(it, view)
                } else {
                    connectToDevice(it, view)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this,
                permissionsToRequest.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
    }

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionRequiredMessage() {
        Toast.makeText(this, "Bluetooth permissions are required to perform this action.", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun populateBondedDevices() {
        val bondedDevices = bluetoothAdapter?.bondedDevices?.map { it.name } ?: listOf()
        bondedDevicesNames.clear()
        bondedDevicesNames.addAll(bondedDevices)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, bondedDevicesNames)
        binding.devicesListView.adapter = adapter
    }

    private fun savePairedDevices() {
        if (connectedDevices.size == 0) {
            Toast.makeText(this, "Please connect exactly 2 devices to create a pair", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceName = binding.deviceNameEditText.text.toString()
        if (deviceName.isEmpty()) {
            Toast.makeText(this, "Please enter a name for the paired devices", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = getSharedPreferences("PairedDevices", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(deviceName, connectedDevices[0].address)  // Save the name of the pair
        editor.apply()
        Toast.makeText(this, "Device pair saved as $deviceName", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, view: View) {
        if (connectedDevices.size >= 2) {
            Toast.makeText(this, "Cannot connect more than 2 devices", Toast.LENGTH_SHORT).show()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket?.connect()
                withContext(Dispatchers.Main) {
                    connectedDevices.add(device)
                    view.setBackgroundColor(ContextCompat.getColor(this@BluetoothConnectionActivity, android.R.color.holo_green_light))
                    Toast.makeText(this@BluetoothConnectionActivity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothConnectionActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
                try {
                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    closeException.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectFromDevice(device: BluetoothDevice, view: View) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket?.close()
                withContext(Dispatchers.Main) {
                    connectedDevices.remove(device)
                    view.setBackgroundColor(ContextCompat.getColor(this@BluetoothConnectionActivity, android.R.color.transparent))
                    Toast.makeText(this@BluetoothConnectionActivity, "Disconnected from ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothConnectionActivity, "Disconnection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }
}
