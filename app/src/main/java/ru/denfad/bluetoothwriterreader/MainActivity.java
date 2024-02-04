package ru.denfad.bluetoothwriterreader;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    public BluetoothAdapter bluetoothAdapter;
    private final String UUID_STRING_WELL_KNOWN_SPP = "00001101-0000-1000-8000-00805F9B34FB";
    private int mState;
    private ThreadConnectBTdevice myThreadConnectBTdevice;
    private ThreadConnected myThreadConnected;
    private AcceptThread acceptThread;
    private final UUID myUUID = UUID.fromString(UUID_STRING_WELL_KNOWN_SPP);
    private Button open;

    private Button close;

    private Button stop;

    private Button more;

    private Button less;

    private TextView textConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ui
        ListView devices = findViewById(R.id.devices);


        open = findViewById(R.id.open);
        close = findViewById(R.id.close);
        stop = findViewById(R.id.stop);
        more = findViewById(R.id.more);
        less = findViewById(R.id.less);

        textConnected = findViewById(R.id.text_connected);

        //bluetooth
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //debug
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            Log.d("TrackerFragment", "Device name: " + device.getName());
            Log.d("TrackerFragment", "Device MAC Address: " + device.getAddress());

        }


        //ui
        List<String> list = new ArrayList<>();
        List<String> MACs = new ArrayList<>();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                list.add(deviceName);
                MACs.add(deviceHardwareAddress);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, list);
        devices.setAdapter(adapter);
        devices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(MACs.get(position));
            myThreadConnectBTdevice = new ThreadConnectBTdevice(device);
            myThreadConnectBTdevice.start();
        });
        open.setOnClickListener(view -> {
            if (mState == STATE_CONNECTED) {
                myThreadConnected.write("2");
            } else {
                Toast.makeText(getApplicationContext(), "Ботинки не подключены", Toast.LENGTH_SHORT).show();
            }
        });

        close.setOnClickListener(view -> {
            if (mState == STATE_CONNECTED) {
                myThreadConnected.write("1");
            } else {
                Toast.makeText(getApplicationContext(), "Ботинки не подключены", Toast.LENGTH_SHORT).show();
            }
        });

        stop.setOnClickListener(view -> {
            if (mState == STATE_CONNECTED) {
                myThreadConnected.write("0");
            } else {
                Toast.makeText(getApplicationContext(), "Ботинки не подключены", Toast.LENGTH_SHORT).show();
            }
        });

        more.setOnClickListener(v -> {
            if (mState == STATE_CONNECTED) {
                myThreadConnected.write("3");
            } else {
                Toast.makeText(getApplicationContext(), "Ботинки не подключены", Toast.LENGTH_SHORT).show();
            }
        });

        less.setOnClickListener(v -> {
            if (mState == STATE_CONNECTED) {
                myThreadConnected.write("4");
            } else {
                Toast.makeText(getApplicationContext(), "Ботинки не подключены", Toast.LENGTH_SHORT).show();
            }
        });
    }

    void makeNotification(String message) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    void connected() {
        textConnected.setText("Подключено к ботинкам");
        textConnected.setBackgroundColor(getResources().getColor(R.color.connected));
        textConnected.setTextColor(getResources().getColor(R.color.white));
    }

    void disconnected() {
        textConnected.setText("Не подключено");
        textConnected.setBackgroundColor(getResources().getColor(R.color.white));
        textConnected.setTextColor(getResources().getColor(R.color.black));
    }


    private class ThreadConnectBTdevice extends Thread { // Поток для коннекта с Bluetooth

        private BluetoothSocket bluetoothSocket = null;

        private ThreadConnectBTdevice(BluetoothDevice device) {

            try {
                int sdk = Build.VERSION.SDK_INT;
                if (sdk >= 10) {
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID);
                } else {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID);
                }
                makeNotification("Creating socket");
            }

            catch (IOException e) {
                e.printStackTrace();

            }
        }


        @Override
        public void run() { // Коннект

            boolean success = false;
            //bluetoothAdapter.cancelDiscovery();

            try {
                bluetoothSocket.connect();
                success = true;
                makeNotification("Bluetooth connected");
                mState = STATE_CONNECTED;
            }
            catch (IOException e) {
                makeNotification("Bluetooth exception while trying connected");
                e.printStackTrace();
                try {
                    bluetoothSocket.close();
                    mState = STATE_DISCONNECTED;
                }
                catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if(success) {  // Если законнектились, тогда открываем панель с кнопками и запускаем поток приёма и отправки данных
                myThreadConnected = new ThreadConnected(bluetoothSocket);
                myThreadConnected.start(); // запуск потока приёма и отправки данных

                connected();

            }
        }


        public void cancel() {
            try {
                bluetoothSocket.close();
                mState = STATE_DISCONNECTED;

            }
            catch (IOException e) {
                e.printStackTrace();
                disconnected();
            }
        }

    } // END ThreadConnectBTdevice:

    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("DEVICE", myUUID);
            } catch (IOException e) {
                disconnected();
                e.printStackTrace();
            }
            mmServerSocket = tmp;
        }

        @Override
        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket = null;
            // Listen to the server socket if we're not connected

            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();

                } catch (IOException e) {
                   disconnected();
                    e.printStackTrace();
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    myThreadConnected = new ThreadConnected(socket);
                    myThreadConnected.start(); // запуск потока приёма и отправки данных
                    makeNotification("Подключен");
                }
            }
        }


        public void cancel() {
            try {
                mmServerSocket.close();
                disconnected();
            } catch (IOException e) {
            }
        }
    }


    private class ThreadConnected extends Thread {    // Поток - приём и отправка данных

        private final InputStream connectedInputStream;
        private final OutputStream connectedOutputStream;
        private final BluetoothSocket mmSocket;
        private String text;

        public ThreadConnected(BluetoothSocket socket) {
            mmSocket=socket;
            InputStream in = null;
            OutputStream out = null;

            try {
                in = mmSocket.getInputStream();
                out = mmSocket.getOutputStream();
            }
            catch (IOException e) {
                disconnected();
            e.printStackTrace();
            }

            connectedInputStream = in;
            connectedOutputStream = out;
        }


        @Override
        public void run() { // Приём данных
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = connectedInputStream.read(buffer);
                    final String strIncom = new String(buffer, 0, bytes);;
                }
                catch (IOException e) {
                   disconnected();
                    e.printStackTrace();
                    break;
                }
            }
        }


        public void write(String text) {
            try {
                connectedOutputStream.write(text.getBytes(StandardCharsets.UTF_8));
                connectedOutputStream.flush();
            } catch (IOException e) {
              disconnected();
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        public void cancel(){
            try{

              disconnected();
                mmSocket.close();
                mState = STATE_DISCONNECTED;
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}