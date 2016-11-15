package tonyg.example.com.bleechoclient.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

/**
 * This class allows us to share Bluetooth resources
 *
 * @author Tony Gaitatzis backupbrain@gmail.com
 * @date 2016-03-06
 */
public class BlePeripheral {
    private static final String TAG = BlePeripheral.class.getSimpleName();


    public static final String CHARACTER_ENCODING = "ASCII";

    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;
    private Context context;

    /** Bluetooth Device stuff **/
    public static final String DEVICE_NAME = "EchoServer";
    public static final UUID SERVICE_UUID = UUID.fromString("0000180c-0000-1000-8000-00805f9b34fb");
    public static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    public static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("00002a56-0000-1000-8000-00805f9b34fb");
    // this is the UUID of the descriptor used to enable and disable notifications
    public static final UUID CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context mContext;

    /** Flow control stuff **/
    private int mNumPacketsTotal;
    private int mNumPacketsSent;
    private int mCharacteristicLength = 20;
    private String mQueuedCharactersticValue;

    /**
     * Create a new BlePeripheral
     *
     * @param context the Activity context
     */
    public BlePeripheral(Context context) {

        mContext = context;
    }

    /**
     * Connect to a Peripheral
     *
     * @param bluetoothDevice the Bluetooth Device
     * @param callback The connection callback
     * @return a connection to the BluetoothGatt
     * @throws Exception if no device is given
     */
    public BluetoothGatt connect(BluetoothDevice bluetoothDevice, BluetoothGattCallback callback) throws Exception {
        if (bluetoothDevice == null) {
            throw new Exception("No bluetooth device provided");
        }
        mBluetoothDevice = bluetoothDevice;
        mBluetoothGatt = bluetoothDevice.connectGatt(mContext, false, callback);
        refreshDeviceCache();
        return mBluetoothGatt;
    }

    /**
     * Disconnect from a Peripheral
     */
    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    /**
     * A connection can only close after a successful disconnect.
     * Be sure to use the BluetoothGattCallback.onConnectionStateChanged event
     * to notify of a successful disconnect
     */
    public void close() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close(); // close connection to Peripheral
            mBluetoothGatt = null; // release from memory
        }
    }
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }


    /**
     * Write next packet in queue if necessary
     *
     * @param value
     * @param characteristic
     * @return the value being written
     * @throws Exception
     */
    public String processIncomingMessage(String value, final BluetoothGattCharacteristic characteristic)  throws Exception {

        if (morePacketsAvailableInQueue()) {
            // NOTE: I honestly don't know why, but this sends too quickly for client to process
            // so we have to delay it
            //final Handler handler = new Handler(Looper.getMainLooper());
            //handler.postDelayed(new Runnable() {
            // @Override
            //public void run() {
            try {
                writePartialValueToCharacteristic(mQueuedCharactersticValue, mNumPacketsSent, characteristic);
            } catch (Exception e) {
                Log.d(TAG, "Unable to send next chunk of message");
            }
            //}
            //}, 100);
        }

        return value;
    }

    /**
     * Clear the GATT Service cache.
     *
     * New in this chapter
     *
     * @return <b>true</b> if the device cache clears successfully
     * @throws Exception
     */
    public boolean refreshDeviceCache() throws Exception {
        Method localMethod = mBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
        if (localMethod != null) {
            return ((Boolean) localMethod.invoke(mBluetoothGatt, new Object[0])).booleanValue();
        }

        return false;
    }

    /**
     * Request a data/value read from a Ble Characteristic
     *
     * @param characteristic
     */
    public void readValueFromCharacteristic(final BluetoothGattCharacteristic characteristic) {
        // Reading a characteristic requires both requesting the read and handling the callback that is
        // sent when the read is successful
        // http://stackoverflow.com/a/20020279
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Write a value to the Characteristic
     *
     * @param value
     * @param characteristic
     * @throws Exception
     */
    public void writeValueToCharacteristic(String value, BluetoothGattCharacteristic characteristic) throws Exception {
        // reset the queue counters, prepare the message to be sent, and send the value to the Characteristic
        mQueuedCharactersticValue = value;
        mNumPacketsSent = 0;
        byte[] byteValue = value.getBytes();
        mNumPacketsTotal = (int) Math.ceil((float) byteValue.length / mCharacteristicLength);
        writePartialValueToCharacteristic(value, mNumPacketsSent, characteristic);

    }


    /**
     * Subscribe or unsubscribe from Characteristic Notifications
     *
     * @param characteristic
     * @param enableNotifications <b>true</b> for "subscribe" <b>false</b> for "unsubscribe"
     */
    public void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic, final boolean enableNotifications) {
        // modified from http://stackoverflow.com/a/18011901/5671180
        // This is a 2-step process
        // Step 1: set the Characteristic Notification parameter locally
        mBluetoothGatt.setCharacteristicNotification(characteristic, enableNotifications);
        // Step 2: Write a descriptor to the Bluetooth GATT enabling the subscription on the Perpiheral
        // turns out you need to implement a delay between setCharacteristicNotification and setvalue.
        // maybe it can be handled with a callback, but this is an easy way to implement
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);

                if (enableNotifications) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                }
                mBluetoothGatt.writeDescriptor(descriptor);
            }
        }, 10);


    }


    /**
     * Write a portion of a larger message to a Characteristic
     *
     * @param message The message being written
     * @param offset The current packet index in queue to be written
     * @param characteristic The Characteristic being written to
     * @throws Exception
     */
    public void writePartialValueToCharacteristic(String message, int offset, BluetoothGattCharacteristic characteristic) throws Exception {
        byte[] temp = message.getBytes();

        mNumPacketsTotal = (int) Math.ceil((float) temp.length / mCharacteristicLength);
        int remainder = temp.length % mCharacteristicLength;

        int dataLength = mCharacteristicLength;
        if (offset >= mNumPacketsTotal) {
            dataLength = remainder;
        }

        byte[] packet = new byte[dataLength];
        for (int localIndex = 0; localIndex < packet.length; localIndex++) {
            int index = (offset * dataLength) + localIndex;
            if (index < temp.length) {
                packet[localIndex] = temp[index];
            } else {
                packet[localIndex] = 0x00;
            }
        }

        // a simpler way to write this might be:
        //System.arraycopy(getCurrentMessage().getBytes(), getCurrentOffset()*mCharacteristicLength, chunk, 0, mCharacteristicLength);
        //chunk[dataLength] = 0x00;


        Log.v(TAG, "Writing message: '" + new String(packet, "ASCII") + "' to " + characteristic.getUuid().toString());
        characteristic.setValue(packet);
        mBluetoothGatt.writeCharacteristic(characteristic);
        mNumPacketsSent++;
    }


    /**
     * Determine if a message has been completely wirtten to a Characteristic or if more data is in queue
     *
     * @return <b>false</b> if all of a message is has been written to a Characteristic, <b>true</b> otherwise
     */
    public boolean morePacketsAvailableInQueue() {
        boolean morePacketsAvailable = mNumPacketsSent < mNumPacketsTotal;
        Log.v(TAG, mNumPacketsSent + " of " + mNumPacketsTotal + " packets sent: "+morePacketsAvailable);
        return morePacketsAvailable;
    }

    /**
     * Determine how much of a message has been written to a Characteristic
     *
     * @return integer representing how many packets have been written so far to Characteristic
     */
    public int getCurrentOffset() {
        return mNumPacketsSent;
    }


    /**
     * Get the current message being written to a Characterstic
     *
     * @return the message in queue for writing to a Characteristic
     */
    public String getCurrentMessage() {
        return mQueuedCharactersticValue;
    }

    // http://stackoverflow.com/a/21300916/5671180
    // more options available at:
    // http://www.programcreek.com/java-api-examples/index.php?class=android.bluetooth.BluetoothGattCharacteristic&method=PROPERTY_NOTIFY

    /**
     * Check if a Characetristic supports write permissions
     * @return Returns <b>true</b> if property is writable
     */
    public static boolean isCharacteristicWritable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    /**
     * Check if a Characetristic has read permissions
     *
     * @return Returns <b>true</b> if property is Readable
     */
    public static boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }

    /**
     * Check if a Characteristic supports Notifications
     *
     * @return Returns <b>true</b> if property is supports notification
     */
    public static boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }


}
