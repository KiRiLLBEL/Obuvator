package ru.denfad.bluetoothwriterreader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.denfad.bluetoothwriterreader.databinding.FragmentHomeBinding
import java.io.IOException
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val pairedDevicesNames = mutableListOf<String>()
    private var bluetoothSocket: BluetoothSocket? = null
    private lateinit var adapter: ArrayAdapter<String>
    private val TAG = "HomeFragment"
    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var isConnected = false

    // Handler and Runnable for periodic polling
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            sendCommandToDevice("AT+VBAT\r\n")
            handler.postDelayed(this, 5 * 60 * 1000) // 5 minutes
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        loadPairedDevices()

        connectionViewModel.connectedDevices.observe(viewLifecycleOwner, Observer { updatePairedDeviceText() })
        binding.devices.setOnItemClickListener { parent, view, position, id ->
            val deviceName = pairedDevicesNames[position]
            val sharedPreferences = requireContext().getSharedPreferences("PairedDevices", Context.MODE_PRIVATE)
            val addresses = sharedPreferences.getString(deviceName, null)

            if (addresses != null) {
                val addressList = addresses.split(",")
                if (addressList.size == 1) {
                    val device = bluetoothAdapter?.getRemoteDevice(addressList[0])
                    if (device != null) {
                        if (connectionViewModel.connectedDevices.value?.contains(device) == true) {
                            disconnectFromDevice(device)
                        } else {
                            connectToDevice(device, view)
                        }
                    }
                } else if (addressList.size == 2) {
                    val device1 = bluetoothAdapter?.getRemoteDevice(addressList[0])
                    val device2 = bluetoothAdapter?.getRemoteDevice(addressList[1])
                    if (device1 != null && device2 != null) {
                        if (connectionViewModel.connectedDevices.value?.contains(device1) == true &&
                            connectionViewModel.connectedDevices.value?.contains(device2) == true) {
                            disconnectFromDevice(device1)
                            disconnectFromDevice(device2)
                        } else {
                            connectToDevice(device1, view)
                            connectToDevice(device2, view)
                        }
                    }
                }
            }
        }

        binding.untieButton.setOnClickListener {
            Log.d(TAG, "Untie button clicked")
            if (isConnected) {
                sendCommandToDevice("AT+RCCW\r\n")
                binding.untieButton.text = "Завязать"
            } else {
                sendCommandToDevice("AT+RCW\r\n")
                binding.untieButton.text = "Развязать"
            }
            isConnected = !isConnected
        }
        // Start the periodic polling
        handler.post(pollRunnable)
    }

    private fun sendCommandToDevice(command: String) {
        val connectedSockets = connectionViewModel.bluetoothSocketMap.value
        connectedSockets?.values?.forEach { socket ->
            try {
                socket.outputStream.write(command.toByteArray())
                // Read the response if the command is AT+VBAT
                if (command.startsWith("AT+VBAT")) {
                    val response = socket.inputStream.bufferedReader().readLine()
                    handleVoltageResponse(response)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun handleVoltageResponse(response: String) {
        if (response.startsWith("OK+VBAT=")) {
            val voltageValue = response.substringAfter("OK+VBAT=").substringBefore(">").toIntOrNull()
            voltageValue?.let {
                val voltageInVolts = it * (3.3 / 4095)
                val percentage = ((voltageInVolts - 3.2) / (4.2 - 3.2)) * 100
                val batteryText = binding.batteryText
                val batteryProgressBar = binding.batteryProgressBar
                batteryText.text = String.format("Battery: %.2f%%", percentage)
                batteryProgressBar.progress = percentage.toInt()
            }
        }
    }

    private fun loadPairedDevices() {
        Log.d(TAG, "loadPairedDevices called")
        val sharedPreferences = requireContext().getSharedPreferences("PairedDevices", Context.MODE_PRIVATE)
        pairedDevicesNames.clear()
        sharedPreferences.all.forEach { entry ->
            pairedDevicesNames.add(entry.key)
        }
        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, pairedDevicesNames)
        binding.devices.adapter = adapter
    }

    private fun connectToDevice(device: BluetoothDevice, view: View) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    return@launch
                }
                val bluetoothSocket =
                    device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket.connect()
                withContext(Dispatchers.Main) {
                    connectionViewModel.addDevice(device, bluetoothSocket)
                    Toast.makeText(requireContext(), "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Connection to ${device.name} failed", Toast.LENGTH_SHORT).show()
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

    @OptIn(DelicateCoroutinesApi::class)
    private fun disconnectFromDevice(device: BluetoothDevice) {
        GlobalScope.launch(Dispatchers.IO) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@launch
            }
            try {
                val socket = connectionViewModel.getSocket(device)
                socket?.close()
                withContext(Dispatchers.Main) {
                    connectionViewModel.removeDevice(device)
                    Toast.makeText(requireContext(), "Disconnected from ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Disconnection from ${device.name} failed", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun updatePairedDeviceText() {
        val sharedPreferences = requireContext().getSharedPreferences("PairedDevices", Context.MODE_PRIVATE)
        val connectedDevices = connectionViewModel.connectedDevices.value
        if (connectedDevices.isNullOrEmpty()) {
            binding.connectedDeviceTextView.text = "No paired device"
        } else {
            val pairedDeviceName = sharedPreferences.all.entries.find { entry ->
                val addressList = entry.value.toString().split(",")
                addressList.all { address ->
                    connectedDevices.any {device -> device.address == address }
                }
            }?.key ?: "No paired device"
            if (pairedDeviceName != null) {
                binding.connectedDeviceTextView.text = "Подключено: $pairedDeviceName"
            } else {
                binding.connectedDeviceTextView.text = "No paired device"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        _binding = null
        // Stop the periodic polling
        handler.removeCallbacks(pollRunnable)
    }
}
