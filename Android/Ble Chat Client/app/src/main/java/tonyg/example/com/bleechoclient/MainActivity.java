

package tonyg.example.com.bleechoclient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

import tonyg.example.com.bleechoclient.ble.BleCommManager;
import tonyg.example.com.bleechoclient.ble.callbacks.BleScanCallbackv21;
import tonyg.example.com.exampleblescan.R;
import tonyg.example.com.bleechoclient.ble.BlePeripheral;
import tonyg.example.com.bleechoclient.ble.callbacks.BleScanCallbackv18;

/**
 * Connect to a BLE Device, list its GATT services
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2015-12-21
 */
public class MainActivity extends AppCompatActivity {
    /** Constants **/
    private static final String TAG = MainActivity.class.getSimpleName();
    private final static int REQUEST_ENABLE_BT = 1;

    /** Bluetooth Stuff **/
    private BleCommManager mBleCommManager;
    private BlePeripheral mBlePeripheral;
    private BluetoothGattCharacteristic mCharacteristic;

    /** UI Stuff **/
    private MenuItem mProgressSpinner;
    private TextView mResponseText, mSendText, mDeviceNameTV, mDeviceAddressTV;
    private Button mSendButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // notify when bluetooth is turned on or off
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBleBroadcastReceiver, filter);

        loadUI();

        mBlePeripheral = new BlePeripheral(this);

    }

    @Override
    public void onPause() {
        super.onPause();
        stopScanning();
        disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Prepare the UI elements
     */
    public void loadUI() {
        mResponseText = (TextView) findViewById(R.id.response_text);
        mSendText = (TextView) findViewById(R.id.write_text);
        mDeviceNameTV = (TextView)findViewById(R.id.broadcast_name);
        mDeviceAddressTV = (TextView)findViewById(R.id.mac_address);

        mSendButton = (Button) findViewById(R.id.write_button);

        mSendButton.setVisibility(View.GONE);
        mSendText.setVisibility(View.GONE);
        mResponseText.setVisibility(View.GONE);
    }


    /**
     * Create the menu
     *
     * @param menu
     * @return <b>true</b> if successful
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        mProgressSpinner = menu.findItem(R.id.scan_progress_item);
        mProgressSpinner.setVisible(true);

        initializeBluetooth();

        return true;
    }


    /**
     * Turn on Bluetooth radio
     */
    public void initializeBluetooth() {
        try {
            mBleCommManager = new BleCommManager(this);
        } catch (Exception e) {
            Log.d(TAG, "Could not initialize bluetooth");
            Log.d(TAG, e.getMessage());
            finish();
        }

        // should prompt user to open settings if Bluetooth is not enabled.
        if (mBleCommManager.getBluetoothAdapter().isEnabled()) {
            startScan();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }


    /**
     * Start scanning for Peripherals
     */
    private void startScan() {
        mDeviceNameTV.setText(R.string.scanning);
        mProgressSpinner.setVisible(true);

        try {
            mBleCommManager.scanForPeripherals(mScanCallbackv18, mScanCallbackv21);
        } catch (Exception e) {
            Log.d(TAG, "Can't create Ble Device Scanner");
        }

    }

    /**
     * Stop scanning for Peripherals
     */
    public void stopScanning() {
        mBleCommManager.stopScanning(mScanCallbackv18, mScanCallbackv21);
    }

    /**
     * Event trigger when BLE Scanning has stopped
     */
    public void onBleScanStopped() {
        mDeviceAddressTV.setText("");
        mDeviceNameTV.setText(R.string.no_perpiheral_found);
        mProgressSpinner.setVisible(false);
    }



    /**
     * Connect to Peripheral
     */
    public void connect(BluetoothDevice bluetoothDevice) {
        mDeviceNameTV.setText(R.string.connecting);
        mProgressSpinner.setVisible(true);
        try {
            mBlePeripheral.connect(bluetoothDevice, mGattCallback);
        } catch (Exception e) {
            mProgressSpinner.setVisible(false);
            Log.d(TAG, "Error connecting to device");
        }
    }

    /**
     * Disconnect from Peripheral
     */
    public void disconnect() {
        mBlePeripheral.disconnect();
        // remove callbacks
        mSendButton.removeCallbacks(null);
        try {
            unregisterReceiver(mBleBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "receiver not registered");
        }
        finish();
    }



    /**
     * Update TextView when a new message is read from a Charactersitic
     * Also scroll to the bottom so that new messages are always in view
     *
     * @param message the Characterstic value to display in the UI as text
     */
    public void updateResponseText(String message) {
        mResponseText.append(message);
        final int scrollAmount = mResponseText.getLayout().getLineTop(mResponseText.getLineCount()) - mResponseText.getHeight();
        // if there is no need to scroll, scrollAmount will be <=0
        if (scrollAmount > 0) {
            mResponseText.scrollTo(0, scrollAmount);
        } else {
            mResponseText.scrollTo(0, 0);
        }
    }

    /**
     * Event trigger when new Peripheral is discovered
     */
    public void onBlePeripheralDiscovered(BluetoothDevice bluetoothDevice, int rssi) {
        // only add the device if
        // - it has a name, on
        // - doesn't already exist in our list, or
        // - is transmitting at a higher power (is closer) than an existing device
        boolean addDevice = false;
        if (bluetoothDevice.getName() != null) {
            if (bluetoothDevice.getName().equals(BlePeripheral.DEVICE_NAME)) {
                addDevice = true;
            }
        }

        if (addDevice) {
            stopScanning();
            connect(bluetoothDevice);
        }
    }

    /**
     * Bluetooth Peripheral connected.  Update UI
     */
    public void onBleConnected(BluetoothDevice device) {
        mDeviceNameTV.setText(device.getName());
        mDeviceAddressTV.setText(device.getAddress());
        mProgressSpinner.setVisible(false);
    }

    /**
     * Bluetooth Peripheral disconnected.  Update UI
     */
    public void onBleDisconnected() {
        mDeviceNameTV.setText("");
        mDeviceAddressTV.setText("");
        mProgressSpinner.setVisible(false);
    }


    /**
     * Bluetooth Peripheral GATT Profile being scanned.  Update UI
     */
    public void onBleServiceDiscovered() {
        mProgressSpinner.setVisible(false);
    }


    /**
     * Characterstic is readable
     */
    public void onCharacteristicReadable(final BluetoothGattCharacteristic characteristic, final BluetoothGatt gatt) {
        Log.d(TAG, "Characteristic is readable");

        // attach callbacks to the buttons and stuff
        mResponseText.setVisibility(View.VISIBLE);
    }


    /**
     * characteristic supports writes.  Update UI
     */
    public void onCharacteristicWritable(final BluetoothGattCharacteristic characteristic, final BluetoothGatt gatt) {
        Log.d(TAG, "Characteristic is writable");
        // send features

        // attach callbacks to the buttons and stuff
        mSendButton.setVisibility(View.VISIBLE);
        mSendText.setVisibility(View.VISIBLE);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Send button clicked");
                String value = mSendText.getText().toString()+"\n";
                try {
                    mBlePeripheral.writeValueToCharacteristic(value, mCharacteristic);

                } catch (Exception e) {
                    Log.d(TAG, "problem sending message through bluetooth");
                }
            }
        });

    }

    /**
     * Clear the input TextView when a Characteristic is successfully written to.
     */
    public void onBleCharacteristicValueWritten() {
        mSendText.setText("");
    }



    /**
     * When the Bluetooth radio turns on, initialize the Bluetooth connection
     */
    private final BroadcastReceiver mBleBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        initializeBluetooth();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        startScan();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                // read more at http://developer.android.com/guide/topics/connectivity/bluetooth-le.html#notification
                final byte[] data = characteristic.getValue();

                String message = "";
                try {
                    message = new String(data, mBlePeripheral.CHARACTER_ENCODING);
                } catch (Exception e) {
                    Log.d(TAG, "Could not convert message byte array to String");
                }


                Log.d(TAG, "received: "+message);

                final String messageText = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateResponseText(messageText);
                    }
                });

                try {
                    mBlePeripheral.processIncomingMessage(message, characteristic);
                } catch (Exception e) {
                    Log.d(TAG, "Could not send next message part");
                }

            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "characteristic written");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBleCharacteristicValueWritten();
                    }
                });
            } else {
                Log.d(TAG, "problem writing characteristic");

            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            mBlePeripheral.readValueFromCharacteristic(characteristic);

        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to device");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBleConnected(bluetoothGatt.getDevice());
                    }
                });

                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from device");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onBleDisconnected();
                    }
                });

                disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bluetoothGatt, int status) {
            Log.d(TAG, "SERVICE DISCOVERED!: ");

            // if services were discovered, then let's iterate through them and display them on screen
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // check if there are matching services and characteristics
                BluetoothGattService service = bluetoothGatt.getService(BlePeripheral.SERVICE_UUID);
                if (service != null) {
                    Log.d(TAG, "service found");
                    final BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(BlePeripheral.READ_CHARACTERISTIC_UUID);
                    final BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(BlePeripheral.WRITE_CHARACTERISTIC_UUID);

                    mCharacteristic = service.getCharacteristic(BlePeripheral.WRITE_CHARACTERISTIC_UUID);
                    if (mBlePeripheral.isCharacteristicReadable(readCharacteristic)) {
                        Log.d(TAG, "characteristic readable");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onCharacteristicReadable(readCharacteristic, bluetoothGatt);
                            }
                        });
                    }


                    if (mBlePeripheral.isCharacteristicWritable(writeCharacteristic)) {
                        Log.d(TAG, "characteristic writeable");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                onCharacteristicWritable(writeCharacteristic, bluetoothGatt);
                            }
                        });
                    }


                    if (mBlePeripheral.isCharacteristicNotifiable(readCharacteristic)) {
                        mBlePeripheral.setCharacteristicNotification(readCharacteristic, true);
                    }
                }


            } else {
                Log.d(TAG, "Something went wrong while discovering GATT services from this device");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleServiceDiscovered();
                }
            });

        }
    };





    /**
     * Use this callback for Android API 21 (Lollipop) or greater
     */
    private final BleScanCallbackv21 mScanCallbackv21 = new BleScanCallbackv21() {
        /**
         * New Peripheral discovered
         *
         * @param callbackType int: Determines how this callback was triggered. Could be one of CALLBACK_TYPE_ALL_MATCHES, CALLBACK_TYPE_FIRST_MATCH or CALLBACK_TYPE_MATCH_LOST
         * @param result a Bluetooth Low Energy Scan Result, containing the Bluetooth Device, RSSI, and other information
         */
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice bluetoothDevice = result.getDevice();
            int rssi = result.getRssi();

            onBlePeripheralDiscovered(bluetoothDevice, rssi);
        }

        /**
         * Several peripherals discovered when scanning in low power mode
         *
         * @param results List: List of scan results that are previously scanned.
         */
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice bluetoothDevice = result.getDevice();
                int rssi = result.getRssi();

                onBlePeripheralDiscovered(bluetoothDevice, rssi);
            }
        }

        /**
         * Scan failed to initialize
         *
         * @param errorCode	int: Error code (one of SCAN_FAILED_*) for scan failure.
         */
        @Override
        public void onScanFailed(int errorCode) {
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED:
                    Log.e(TAG, "Fails to start scan as BLE scan with the same settings is already started by the app.");
                    break;
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    Log.e(TAG, "Fails to start scan as app cannot be registered.");
                    break;
                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG, "Fails to start power optimized scan as this feature is not supported.");
                    break;
                default: // SCAN_FAILED_INTERNAL_ERROR
                    Log.e(TAG, "Fails to start scan due an internal error");

            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleScanStopped();
                }
            });
        }

        /**
         * Scan completed
         */
        public void onScanComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleScanStopped();
                }
            });

        }
    };


    /**
     * Use this callback for Android API 18, 19, and 20 (before Lollipop)
     */
    private BleScanCallbackv18 mScanCallbackv18 = new BleScanCallbackv18() {

        /**
         *  Bluetooth LE Scan complete - timer expired out while searching for bluetooth devices
         */
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
            onBlePeripheralDiscovered(bluetoothDevice, rssi);
        }

        @Override
        public void onScanComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onBleScanStopped();
                }
            });
        }
    };



}
