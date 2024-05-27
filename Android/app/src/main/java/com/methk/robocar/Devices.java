package com.methk.robocar;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Set;

public class Devices extends AppCompatActivity {

    private static final String TAG = "DevicesActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_CONNECT = 2;
    private static final int REQUEST_BLUETOOTH_SCAN = 3;

    // Widgets
    private ListView devicesList;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter = null;
    private Set<BluetoothDevice> pairedDevices;
    private String MACAddress;
    protected static String EXTRA_ADDRESS = "MacAddress";
    protected static ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } // Restart this activity if bluetooth is disconnected
        }, new IntentFilter(BluetoothConnection.INTENT_FILTER_STOP));

        FloatingActionButton fab = findViewById(R.id.fab_refresh);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pairedDevicesList();
            }
        });

        // Initialize widgets
        devicesList = findViewById(R.id.paired_dev_listview);

        // Initialize bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check and request Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQUEST_BLUETOOTH_CONNECT);
            } else {
                pairedDevicesList();
            }
        } else {
            pairedDevicesList();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT || requestCode == REQUEST_BLUETOOTH_SCAN) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pairedDevicesList();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required to use this feature.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            pairedDevicesList();
        }
    }

    // List all paired bluetooth devices
    private void pairedDevicesList() {
        try {
            if (bluetoothAdapter == null) { // This device has no bluetooth adapter
                Toast.makeText(getApplicationContext(), getString(R.string.devices_bt_not_avail), Toast.LENGTH_LONG).show();
                finish();
            } else if (!bluetoothAdapter.isEnabled()) { // This device has a bluetooth adapter but it's turned off
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT); // Intent to turn on the bluetooth adapter
            } else {
                pairedDevices = bluetoothAdapter.getBondedDevices();
                ArrayList<String> list = new ArrayList<>(); // ArrayList with name and MAC address of paired devices

                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice bt : pairedDevices) {
                        list.add(bt.getName() + "\n" + bt.getAddress());
                    }
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.devices_dev_not_found), Toast.LENGTH_LONG).show();
                }

                // Display paired devices in the listview
                devicesList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list));
                devicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
                        try {
                            // MAC address are the last 17 characters of the textview clicked
                            String info = ((TextView) v).getText().toString();
                            MACAddress = info.substring(info.length() - 17);
                            new StartService().execute(); // AsyncTask to start the bluetooth service
                        } catch (Exception e) {
                            Log.e(TAG, "Error while starting service", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in pairedDevicesList", e);
        }
    }

    private class StartService extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            try {
                progressDialog = ProgressDialog.show(Devices.this, getString(R.string.devices_dialog_title), getString(R.string.devices_dialog_content));  // Loading dialog
            } catch (Exception e) {
                Log.e(TAG, "Error while showing progress dialog", e);
            }
        }

        @Override
        protected Void doInBackground(Void... devices) {
            try {
                startService(new Intent(Devices.this, BluetoothConnection.class).putExtra(EXTRA_ADDRESS, MACAddress));
            } catch (Exception e) {
                Log.e(TAG, "Error while starting BluetoothConnection service", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while dismissing progress dialog", e);
            }
        }
    }
}
