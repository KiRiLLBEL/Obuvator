package ru.denfad.bluetoothwriterreader

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.*
import ru.denfad.bluetoothwriterreader.databinding.FragmentConnectionBinding
import java.io.IOException
import java.util.*

class ConnectionFragment : Fragment() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DeviceAdapter
    private val allDevicesNames = mutableListOf<String>()
    private val TAG = "ConnectionFragment"
    private val connectionViewModel: ConnectionViewModel by activityViewModels()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
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
                    return
                }
                device?.let {
                    val deviceName =
                        it.name ?: return@let
                    if (!allDevicesNames.contains(deviceName)) {
                        allDevicesNames.add(deviceName)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        adapter = DeviceAdapter(requireContext(), allDevicesNames)
        binding.devicesListView.adapter = adapter

        checkAndRequestPermissions()
        startDiscovery()

        binding.saveButton.setOnClickListener {
            Log.d(TAG, "Save button clicked")
            savePairedDevices()
        }

        fun logBondedDevices() {
            val bondedDevices = bluetoothAdapter?.bondedDevices
            if (bondedDevices.isNullOrEmpty()) {
                Log.d(TAG, "No bonded devices found")
            } else {
                Log.d(TAG, "Bonded devices:")
                for (device in bondedDevices) {
                    Log.d(TAG, "Device name: ${device.name}, Device address: ${device.address}")
                }
            }
        }


        binding.devicesListView.setOnItemClickListener { parent, view, position, id ->
            val deviceName = allDevicesNames[position]
            Log.d(TAG, "Device selected: $deviceName")
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionRequiredMessage()
                return@setOnItemClickListener
            }
            val device = bluetoothAdapter?.bondedDevices?.find { it.name == deviceName }
            device?.let {
                if (connectionViewModel.connectedDevices.value?.contains(it) == true) {
                    disconnectFromDevice(it, view)
                } else {
                    connectToDevice(it, view)
                }
            }
        }

        connectionViewModel.connectedDevices.observe(viewLifecycleOwner) { devices ->
            val deviceNames = devices.map { it.name }
            adapter.updateConnectedDevices(deviceNames)
            updateDeviceList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "checkAndRequestPermissions called")
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
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(),
                permissionsToRequest.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        allDevicesNames.clear()
        bluetoothAdapter?.startDiscovery()

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        requireContext().registerReceiver(discoveryReceiver, filter)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showPermissionRequiredMessage() {
        Log.d(TAG, "showPermissionRequiredMessage called")
        Toast.makeText(requireContext(), "Bluetooth permissions are required to perform this action.", Toast.LENGTH_SHORT).show()
    }

    private fun savePairedDevices() {
        Log.d(TAG, "savePairedDevices called")
        if (connectionViewModel.connectedDevices.value?.size == 0) {
            Toast.makeText(requireContext(), "Please connect exactly 2 devices to create a pair", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceName = binding.deviceNameEditText.text.toString()
        if (deviceName.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a name for the paired devices", Toast.LENGTH_SHORT).show()
            return
        }

        val sharedPreferences = requireContext().getSharedPreferences("PairedDevices", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val devices = connectionViewModel.connectedDevices.value!!
        var deviceAddresses = ""
        if(devices.size == 1) {
            deviceAddresses = devices[0].address
        } else {
            deviceAddresses = "${devices[0].address},${devices[1].address}"
        }

        editor.putString(deviceName, deviceAddresses)
        editor.apply()
        Toast.makeText(requireContext(), "Device pair saved as $deviceName", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice, view: View) {
        Log.d(TAG, "connectToDevice called: ${device.name}")
        if (connectionViewModel.connectedDevices.value?.size ?: 0 >= 2) {
            Toast.makeText(requireContext(), "Cannot connect more than 2 devices", Toast.LENGTH_SHORT).show()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket.connect()
                withContext(Dispatchers.Main) {
                    connectionViewModel.addDevice(device, bluetoothSocket)
                    adapter.notifyDataSetChanged()
                    view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                    Toast.makeText(requireContext(), "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
        Log.d(TAG, "disconnectFromDevice called: ${device.name}")
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val socket = connectionViewModel.getSocket(device)
                socket?.close()
                withContext(Dispatchers.Main) {
                    connectionViewModel.removeDevice(device)
                    adapter.notifyDataSetChanged()
                    view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
                    Toast.makeText(requireContext(), "Disconnected from ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Disconnection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun updateDeviceList() {
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
            return
        }
        val connectedDeviceNames = connectionViewModel.connectedDevices.value?.map {
            it.name } ?: listOf()
        allDevicesNames.clear()
        allDevicesNames.addAll(connectedDeviceNames)
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")
        _binding = null
        requireContext().unregisterReceiver(discoveryReceiver)
    }
}
