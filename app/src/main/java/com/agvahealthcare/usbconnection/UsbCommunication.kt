package com.agvahealthcare.usbconnection

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.*
import android.widget.Toast
import com.felhr.usbserial.*
import java.util.concurrent.atomic.AtomicBoolean

class UsbCommunication : Service(), SerialPortCallback {

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
        const val MESSAGE_FROM_SERIAL_PORT = 0
        const val SYNC_READ = 3
        var SERVICE_CONNECTED = false
    }

    private var serialPorts: List<UsbSerialDevice>? = null
    private var context: Context? = null
    private var usbManager: UsbManager? = null
    private var builder: SerialPortBuilder? = null
    private val binder: IBinder = UsbBinder()
    private var mHandler: Handler? = null
    private var readThreadCOM1: ReadThreadCOM? = null
    private var readThreadCOM2: ReadThreadCOM? = null
    private var writeHandler: Handler? = null
    private var writeThread: WriteThread? = null


    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context, arg1: Intent) {
            if (arg1.action == ACTION_USB_ATTACHED) {
                val ret = builder!!.openSerialPorts(context, BAUD_RATE,
                    UsbSerialInterface.DATA_BITS_8, UsbSerialInterface.STOP_BITS_1,
                    UsbSerialInterface.PARITY_NONE, UsbSerialInterface.FLOW_CONTROL_OFF)

                if (!ret) Toast.makeText(context, "Couldn't open the device", Toast.LENGTH_SHORT)
                    .show()
            } else if (arg1.action == ACTION_USB_DETACHED) {
                val usbDevice = arg1.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                val ret = builder!!.disconnectDevice(usbDevice)
                if (ret) Toast.makeText(context, "Usb device disconnected", Toast.LENGTH_SHORT)
                    .show() else Toast.makeText(context, "Usb device wasn't a serial port",
                    Toast.LENGTH_SHORT).show()
                val intent = Intent(ACTION_USB_DISCONNECTED)
                arg0.sendBroadcast(intent)
            }
        }
    }

    override fun onCreate() {
        context = this
        SERVICE_CONNECTED = true
        setFilter()
        usbManager = getSystemService(USB_SERVICE) as UsbManager
        builder = SerialPortBuilder.createSerialPortBuilder(this)
        val ret = builder?.openSerialPorts(context,
            BAUD_RATE,
            UsbSerialInterface.DATA_BITS_8,
            UsbSerialInterface.STOP_BITS_1,
            UsbSerialInterface.PARITY_NONE,
            UsbSerialInterface.FLOW_CONTROL_OFF)
        if (!ret!!) Toast.makeText(context, "No Usb serial ports available", Toast.LENGTH_SHORT)
            .show()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    fun write(data: ByteArray?, port: Int) {
        if (writeThread != null)
            writeHandler?.obtainMessage(0, port, 0, data)?.sendToTarget()
    }

    fun setHandler(mHandler: Handler?) {
        this.mHandler = mHandler
    }


    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_DETACHED)
        filter.addAction(ACTION_USB_ATTACHED)
        registerReceiver(usbReceiver, filter)
    }


    inner class UsbBinder : Binder() {
        fun getService(): UsbCommunication {
            return this@UsbCommunication
        }
    }

    inner class ReadThreadCOM(
        private val port: Int,
        private val inputStream: SerialInputStream?,
    ) : Thread() {
        private val keep = AtomicBoolean(true)
        override fun run() {
            while (keep.get()) {
                if (inputStream == null) return
                val value = inputStream.read()
                if (value != -1) {
                    val str: String = toASCII(value)
                    mHandler?.obtainMessage(SYNC_READ,
                        port, 0, str)?.sendToTarget()
                }
            }
        }

        fun setKeep(keep: Boolean) {
            this.keep.set(keep)
        }
    }

    private fun toASCII(value: Int): String {
        val length = 4
        val builder = StringBuilder(length)
        for (i in length - 1 downTo 0) {
            builder.append((value shr 8 * i and 0xFF).toChar())
        }
        return builder.toString()
    }

    inner class WriteThread : Thread() {
        @SuppressLint("HandlerLeak")
        override fun run() {
            Looper.prepare()
            writeHandler = object : Handler() {
                override fun handleMessage(msg: Message) {
                    val port = msg.arg1
                    val data = msg.obj as ByteArray
                    if (port <= serialPorts!!.size - 1) {
                        val serialDevice: UsbSerialDevice = serialPorts!![port]
                        serialDevice.outputStream.write(data)
                    }
                }
            }
            Looper.loop()
        }
    }

    override fun onSerialPortsDetected(serialPorts: MutableList<UsbSerialDevice>?) {
        this.serialPorts = serialPorts

        if (serialPorts!!.size == 0) return

        if (writeThread == null) {
            writeThread = WriteThread()
            writeThread!!.start()
        }

        var index = 0

        if (readThreadCOM1 == null && index <= serialPorts.size - 1 && serialPorts[index].isOpen
        ) {
            readThreadCOM1 = ReadThreadCOM(index,
                serialPorts[index].inputStream)
            readThreadCOM1!!.start()
        }

        index++
        if (readThreadCOM2 == null && index <= serialPorts.size - 1 && serialPorts[index].isOpen
        ) {
            readThreadCOM2 = ReadThreadCOM(index,
                serialPorts[index].inputStream)
            readThreadCOM2!!.start()
        }
    }

}