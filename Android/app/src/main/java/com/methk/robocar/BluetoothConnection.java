package com.methk.robocar;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class BluetoothConnection extends Service {

    // Handler
    protected static final int RECEIVE_MESSAGE = 0;
    private Handler messageHandler;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    protected BluetoothThread bluetoothThread;
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String MACAddress;
    private boolean stopThread;

    // Communication
    protected static String INTENT_FILTER_COMM = "com.methk.robocar.communication";
    protected static String INTENT_FILTER_STOP = "com.methk.robocar.disconnect";
    private final IBinder binder = new LocalBinder();

    private Intent stopActivityIntent = new Intent(INTENT_FILTER_STOP); // Intent to stop activities when bluetooth disconnects
    private LocalBroadcastManager manager; // Communication with activities
    private final BroadcastReceiver bluetoothConnectionStatusReceiver = new BroadcastReceiver() { // Check if bluetooth is disconnected
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
                sendCommand('S');
                manager.sendBroadcast(stopActivityIntent);
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        messageHandler = new MessageHandler();

        manager = LocalBroadcastManager.getInstance(BluetoothConnection.this);
        registerReceiver(bluetoothConnectionStatusReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.hasExtra(Devices.EXTRA_ADDRESS)) {
            MACAddress = intent.getStringExtra(Devices.EXTRA_ADDRESS); // MAC address of the chosen device
            connectBluetooth();
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth құрылғысының MAC мекенжайы қажет.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i("INFO", "onDestroy: ");

        sendCommand(Controller.STOP);

        messageHandler.removeCallbacksAndMessages(null); // Close handler
        unregisterReceiver(bluetoothConnectionStatusReceiver);

        if (bluetoothThread != null) { // Close thread
            stopThread = true;
            bluetoothThread.closeStreams();
        }

        if (bluetoothSocket != null) { // Close bluetooth connection
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "Bluetooth сокетін жабу қатесі", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void connectBluetooth() {
        if (MACAddress != null && !MACAddress.equals("") && MACAddress.length() == 17) { // Valid MACAddress
            if (bluetoothSocket == null) { // If socket is not taken or device not connected
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    bluetoothSocket = bluetoothAdapter.getRemoteDevice(MACAddress).createInsecureRfcommSocketToServiceRecord(BTMODULEUUID); // This connection is not secure (mitm attacks)
                    bluetoothSocket.connect();
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "Қосылым сәтсіз аяқталды. Бірдеңе дұрыс болмады.", Toast.LENGTH_LONG).show();
                    stopSelf();
                    if(Devices.progressDialog.isShowing()) Devices.progressDialog.dismiss();
                    return;
                }

                BluetoothAdapter.getDefaultAdapter().cancelDiscovery(); // Discovery process is heavy

                if(!Controller.isActive)
                    startActivity(new Intent(this, Controller.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                Toast.makeText(getApplicationContext(), "Қосылды", Toast.LENGTH_LONG).show();

                // Start bluetooth connection thread
                bluetoothThread = new BluetoothThread(bluetoothSocket);
                bluetoothThread.start();
            }
            if(Devices.progressDialog.isShowing()) Devices.progressDialog.dismiss();
        }

    }

    protected void sendCommand(int input) {
        if(bluetoothThread != null) bluetoothThread.sendCommand(input);
    }

    protected class LocalBinder extends Binder {
        BluetoothConnection getService() {
            return BluetoothConnection.this;
        }
    }

    private class BluetoothThread extends Thread {
        private final InputStream inStream;
        private final OutputStream outStream;

        public BluetoothThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                stopSelf();
            }

            inStream = tmpIn;
            outStream = tmpOut;
        }

        public void run() {
            while(!stopThread) {
                try {
                    int bytesAvailable = inStream.available();
                    if(bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        int bytes = inStream.read(packetBytes);
                        messageHandler.obtainMessage(RECEIVE_MESSAGE, bytes, -1, packetBytes).sendToTarget();
                        this.sleep(200);
                    }
                } catch (IOException e) {
                    stopSelf();
                    break;
                } catch (InterruptedException e) {
                    stopSelf();
                    break;
                }
            }
        }

        public void sendCommand(int input) {
            try {
                outStream.write(input);
            } catch (IOException e) {
                stopSelf();
            }
        }

        public void closeStreams() {
            try {
                inStream.close();
                outStream.close();
            } catch (IOException e) {
                stopSelf();
            }
        }
    }

    private class MessageHandler extends Handler {
        private int readBufferPosition = 0;
        private byte[] readBuffer = new byte[1024];
        private Intent intent = new Intent(INTENT_FILTER_COMM);

        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case RECEIVE_MESSAGE:
                    byte[] readBuf = (byte[]) msg.obj;
                    int bytesAvailable = readBuf.length;
                    for(int i = 0; i < bytesAvailable; i++) {
                        byte b = readBuf[i];
                        if(b == 35) { // 35 is the ASCII code for '#'
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            String data = "";
                            try {
                                data = new String(encodedBytes, "US-ASCII");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            readBufferPosition = 0;
                            if(!data.equals("")) {
                                try {
                                    intent.putExtra("data", Integer.parseInt(data));
                                    manager.sendBroadcast(intent);
                                } catch (NumberFormatException e) {
                                    Toast.makeText(getApplicationContext(), "HT-05 модулінің мәселесі. Arduino құрылғысын қайта іске қосыңыз.", Toast.LENGTH_LONG).show();
                                }
                            }
                        }

                        else if (b == 36) { // 35 is the ASCII code for '#'
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            String data = "";
                            try {
                                data = new String(encodedBytes, "US-ASCII");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            readBufferPosition = 0;
                            if(!data.equals("")) {
                                try {

                                    intent.putExtra("weight", Float.parseFloat(data));

                                    manager.sendBroadcast(intent);
                                } catch (NumberFormatException e) {
                                    Toast.makeText(getApplicationContext(), "HT-05 модулінің мәселесі. Arduino құрылғысын қайта іске қосыңыз.", Toast.LENGTH_LONG).show();
                                }
                            }
                        }
                        else
                            readBuffer[readBufferPosition++] = b;
                    }
                    break;
            }
        }
    }

}
