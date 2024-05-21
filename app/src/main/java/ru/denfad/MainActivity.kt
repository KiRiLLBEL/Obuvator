package ru.denfad.bluetoothwriterreader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import ru.denfad.bluetoothwriterreader.databinding.ActivityMainBinding
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val APP_NAME = "BluetoothApp"
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        checkAndRequestPermissions()

        populateBondedDevices()

        binding.devices.setOnItemClickListener { parent, view, position, id ->
            val deviceName = parent.getItemAtPosition(position) as String
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionRequiredMessage()
                return@setOnItemClickListener
            }
            bluetoothDevice = bluetoothAdapter?.bondedDevices?.find { it.name == deviceName }
            bluetoothDevice?.let {
                connectToDevice(it)
            }
        }

        binding.open.setOnClickListener {
            if (isPermissionGranted()) {
                sendMessage("OPEN")
            } else {
                showPermissionRequiredMessage()
            }
        }

        binding.stop.setOnClickListener {
            if (isPermissionGranted()) {
                sendMessage("STOP")
            } else {
                showPermissionRequiredMessage()
            }
        }

        binding.close.setOnClickListener {
            if (isPermissionGranted()) {
                sendMessage("CLOSE")
            } else {
                showPermissionRequiredMessage()
            }
        }

        binding.more.setOnClickListener {
            if (isPermissionGranted()) {
                sendMessage("MORE")
            } else {
                showPermissionRequiredMessage()
            }
        }

        binding.less.setOnClickListener {
            if (isPermissionGranted()) {
                sendMessage("LESS")
            } else {
                showPermissionRequiredMessage()
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
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, bondedDevices)
        binding.devices.adapter = adapter
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun connectToDevice(device: BluetoothDevice) {
        GlobalScope.launch(Dispatchers.IO) {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothAdapter?.cancelDiscovery()
            try {
                bluetoothSocket?.connect()
                withContext(Dispatchers.Main) {
                    binding.textConnected.text = "Connected to ${bluetoothDevice?.name}"
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try {
                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    closeException.printStackTrace()
                }
                withContext(Dispatchers.Main) {
                    binding.textConnected.text = "Connection failed"
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendMessage(message: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket?.outputStream?.write(message.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                populateBondedDevices()
            } else {
                showPermissionRequiredMessage()
            }
        }
    }
}
