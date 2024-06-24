package com.agvahealthcare.usbconnection

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback
import java.io.UnsupportedEncodingException

class UsbConnection : Service() {

    companion object {
        const val TAG = "UsbConnection"
        const val ACTION_USB_READY = "com.agvahealthcare.connectivityservices.USB_READY"
        const val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        const val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED"
        const val ACTION_USB_NOT_SUPPORTED = "com.agvahealthcare.usbconnection.USB_NOT_SUPPORTED"
        const val ACTION_NO_USB = "com.agvahealthcare.usbconnection.NO_USB"
        const val ACTION_USB_PERMISSION_GRANTED =
            "com.agvahealthcare.usbconnection.USB_PERMISSION_GRANTED"
        const val ACTION_USB_PERMISSION_NOT_GRANTED =
            "com.agvahealthcare.usbconnection.USB_PERMISSION_NOT_GRANTED"
        const val ACTION_USB_DISCONNECTED = "com.agvahealthcare.usbconnection.USB_DISCONNECTED"
        const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        const val BAUD_RATE = 9600 // BaudRate. Change this value if you need
        const val BAUD_RATE_1 = 12500 // BaudRate. Change this value if you need

        const val MESSAGE_FROM_SERIAL_PORT = 0
        var SERVICE_CONNECTED = false
    }

    private var mHandler: Handler? = null
    private var usbManager: UsbManager? = null
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var serialPort: UsbSerialDevice? = null
    private var context: Context? = null
    private var serialPortConnected = false
    private val binder: IBinder = UsbBinder()


    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */

    private val mCallback = UsbReadCallback { arg0 ->
            try {
                val data = String(arg0, Charsets.UTF_8)
                if (mHandler != null) mHandler!!.obtainMessage(MESSAGE_FROM_SERIAL_PORT, data).sendToTarget()
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }

    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     */
    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent) {
            if (arg1.action == ACTION_USB_PERMISSION) {
                val granted = arg1.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) // User accepted our USB connection. Try to open the device as a serial port
                {
                    val intent = Intent(ACTION_USB_PERMISSION_GRANTED)
                    arg0.sendBroadcast(intent)
                    connection = usbManager?.openDevice(device)
                    ConnectionThread().start()
                } else
                // User not accepted our USB connection. Send an Intent to the Main Activity
                {
                    val intent = Intent(ACTION_USB_PERMISSION_NOT_GRANTED)
                    arg0.sendBroadcast(intent)
                }
            } else if (arg1.action == ACTION_USB_ATTACHED) {
                if (!serialPortConnected)
                    findSerialPortDevice() // A USB device has been attached. Try to open it as a Serial port
            } else if (arg1.action == ACTION_USB_DETACHED) {
                // Usb device was disconnected. send an intent to the Main Activity
                val intent = Intent(ACTION_USB_DISCONNECTED)
                arg0.sendBroadcast(intent)
                if (serialPortConnected) {
                    serialPort!!.close()
                }
                serialPortConnected = false
            }
        }
    }

    /*
    * onCreate will be executed when service is started. It configures an IntentFilter to listen for
    * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
    */

    override fun onCreate() {
        context = this
        serialPortConnected = false
        SERVICE_CONNECTED = true
        setFilter()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        findSerialPortDevice()
    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serialPort?.close()
        unregisterReceiver(usbReceiver)
        SERVICE_CONNECTED = false
    }

    /*
     * This function will be called from MainActivity to write data through Serial Port
     */
    fun write(data: ByteArray?) {
        serialPort?.write(data)
    }

    fun setHandler(mHandler: Handler?) {
        this.mHandler = mHandler
    }

    private fun findSerialPortDevice() {
        /*
        This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        */
        val usbDevices = usbManager!!.deviceList
        if (usbDevices.isNotEmpty()) {

            // first, dump the hashmap for diagnostic purposes
            for ((_, value) in usbDevices) {
                device = value
                Log.d(TAG, String.format("USBDevice.HashMap (vid:pid) (%X:%X)-%b class:%X:%X name:%s",
                        device?.vendorId, device?.productId, UsbSerialDevice.isSupported(device),
                        device?.deviceClass, device?.deviceSubclass, device?.deviceName))
            }
            for ((_, value) in usbDevices) {
                device = value
                val deviceVID = device?.vendorId
                val devicePID = device?.productId

                if (UsbSerialDevice.isSupported(device)) {
                    // There is a supported device connected - request permission to access it.
                    requestUserPermission()
                    break
                } else {
                    connection = null
                    device = null
                }
            }
            if (device == null) {
                // There are no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                val intent = Intent(ACTION_NO_USB)
                sendBroadcast(intent)
            }
        } else {
            Log.d(TAG, "findSerialPortDevice() usbManager returned empty device list.")
            // There is no USB devices connected. Send an intent to MainActivity
            val intent = Intent(ACTION_NO_USB)
            sendBroadcast(intent)
        }
    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_DETACHED)
        filter.addAction(ACTION_USB_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private fun requestUserPermission() {
        Log.d(TAG, String.format("requestUserPermission(%X:%X)", device!!.vendorId, device!!.productId))
        val mPendingIntent = PendingIntent.getBroadcast(this,
            0, Intent(ACTION_USB_PERMISSION), 0)
        usbManager?.requestPermission(device, mPendingIntent)
    }

   inner class UsbBinder : Binder() {
        fun getService(): UsbConnection{
            return this@UsbConnection
        }
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */


    inner class ConnectionThread : Thread() {
        override fun run() {
            
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection)
            if (serialPort != null) {
                if (serialPort!!.open()) {
                    serialPortConnected = true
                    serialPort?.setBaudRate(BAUD_RATE)
                    serialPort?.setDataBits(UsbSerialInterface.DATA_BITS_8)
                    serialPort?.setStopBits(UsbSerialInterface.STOP_BITS_1)
                    serialPort?.setParity(UsbSerialInterface.PARITY_NONE)
                    serialPort?.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                    serialPort?.read(mCallback)

                    // Everything went as expected. Send an intent to MainActivity
                    val intent = Intent(ACTION_USB_READY)
                    context?.sendBroadcast(intent)
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                val intent = Intent(ACTION_USB_NOT_SUPPORTED)
                context?.sendBroadcast(intent)
            }



        }
    }


}


