package com.agvahealthcare.usbconnection

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.felhr.usbserial.UsbSerialDevice

class UsbConnect_2 : Service() {

    private val DEFAULT_BAUD_RATE_2 = 9600

    private val ARDUINO_VID_2 = 1003

    private val binder: IBinder = LocalBinder()



    companion object {
        @JvmStatic
        val ACTION_DATA_AVAILABLE_2 = "com.agvahealthcare.multiport.ACTION_DATA_AVAILABLE_2"

        var ACTION_USB_PERMISSION_2 = "com.agvahealthcare.multiport.USB_PERMISSION"

    }

    private var usbManager: UsbManager? = null

    private var usbDevice2: UsbDevice? = null

    private var usb2: UsbSerialDevice? = null


    override fun onCreate() {
        super.onCreate()

        registerReceiver(usbReceiver, getPermissionFilter())
        registerReceiver(connReceiver, getConnectionFilter())
        usbManager = getSystemService(USB_SERVICE) as UsbManager

    }


    private fun getPermissionFilter(): IntentFilter  = IntentFilter().apply {
        addAction(ACTION_USB_PERMISSION_2)
    }


    private fun getConnectionFilter(): IntentFilter  = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
    }



    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        unregisterReceiver(connReceiver)
        usb2 = null
        super.onDestroy()
        Log.e("SERVICE_CALL" ,"DESTROY")

    }

    private val connReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Toast.makeText(this@UsbConnect_2, "usb attach", Toast.LENGTH_LONG).show()
                        val udev = it.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE);
                        val vid = udev?.vendorId
                        val pid = udev?.productId
                        Log.e("USB_1" , "VID =$vid || PID =$pid" )

                     if (vid == ARDUINO_VID_2) {
                            Toast.makeText(this@UsbConnect_2, "Product_Id_2 $vid", Toast.LENGTH_LONG)
                                .show()
                            requestUsb2(udev)
                        }
                    }
                     // Detach print a message which device got detached
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> Toast.makeText(this@UsbConnect_2, "usb de-attach", Toast.LENGTH_LONG).show()

                }
            }
        }

    }


    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
                        if (ACTION_USB_PERMISSION_2 == intent.action) {
                    usbDevice2 = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        usbDevice2?.apply {
                            //call method to set up device communication

                            Log.e("USBCHECK" , "Permission 2 catch")
                            if (startConnectionPort2()) {
                                startReading2();
                            }
                        }
                    } else {
                        // Log.d(TAG, "permission denied for device $device")
                        Toast.makeText(this@UsbConnect_2, "Permission Denied", Toast.LENGTH_LONG)
                            .show()

                    }

            }
        }
    }


    //START_READING_PORT_2
    private fun startReading2() {
        usb2?.apply {

            if (isOpen) {
                Log.e("USB_2_OPEN", ""+isOpen);

                read {
                    Log.e("USB_2_READ", String(it));
                    sendBroadcast(Intent(ACTION_DATA_AVAILABLE_2).apply {
                        putExtra("data", String(it))
                    })
                }
            }
        }

    }

    //CONNECTION_PORT_2
    private fun startConnectionPort2(): Boolean {
        if (usbManager != null) {
            if (usbDevice2 != null && usb2 == null) {
                Log.i("USB_2", "Detected device extracting to serial");
                try {
                    usb2 = UsbSerialDevice.createUsbSerialDevice(
                        usbDevice2, usbManager!!.openDevice(usbDevice2))
                    if (usb2?.open() ?: false) {
                        usb2?.setDataBits(UsbSerialDevice.DATA_BITS_8)
                        usb2?.setStopBits(UsbSerialDevice.STOP_BITS_1)
                        usb2?.setParity(UsbSerialDevice.PARITY_NONE)
                        usb2?.setFlowControl(UsbSerialDevice.FLOW_CONTROL_OFF)
                        usb2?.setBaudRate(DEFAULT_BAUD_RATE_2)
                        Log.i("USB_2_READ", "Connection established")
                        return true
                    }
                } catch (e: Exception) {
                    Log.i("USB_2_CHECK", "Permissions are not granted to USB")
                    e.printStackTrace()

                }
            } else {
                Log.i("USB_2", "No device detected");

            }
        }

        return false
    }


    //REQUEST_USB_2
    fun requestUsb2(udev: UsbDevice) {
        if (usbManager != null) {

            // USB_PORT 2
            if (usbDevice2 != null && usb2 == null) {
                usbManager!!.requestPermission(
                    udev,
                    PendingIntent.getBroadcast(this, 1,  Intent(ACTION_USB_PERMISSION_2), 0)
                )
            }
        }

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("USB_SERVICE", "Starting usb service");
        Log.e("SERVICE_CALL" ,"START")

        return START_STICKY
    }

    inner class LocalBinder : Binder() {
        fun getService(): UsbConnect_2{
            return this@UsbConnect_2
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder


}