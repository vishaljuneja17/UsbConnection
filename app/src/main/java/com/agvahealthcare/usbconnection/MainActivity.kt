package com.agvahealthcare.usbconnection

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.agvahealthcare.usbconnection.UsbConnection.Companion.ACTION_NO_USB
import com.agvahealthcare.usbconnection.UsbConnection.Companion.ACTION_USB_DISCONNECTED
import com.agvahealthcare.usbconnection.UsbConnection.Companion.ACTION_USB_NOT_SUPPORTED
import com.agvahealthcare.usbconnection.UsbConnection.Companion.ACTION_USB_PERMISSION_GRANTED
import com.agvahealthcare.usbconnection.UsbConnection.Companion.ACTION_USB_PERMISSION_NOT_GRANTED
import com.agvahealthcare.usbconnection.UsbConnection.Companion.MESSAGE_FROM_SERIAL_PORT
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    /*
     * Notifications from UsbService will be received here.
     */
    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION_GRANTED -> Toast.makeText(context,
                    "USB Ready",
                    Toast.LENGTH_SHORT).show()
                ACTION_USB_PERMISSION_NOT_GRANTED -> Toast.makeText(context,
                    "USB Permission not granted",
                    Toast.LENGTH_SHORT).show()
                ACTION_NO_USB -> Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT)
                    .show()
                ACTION_USB_DISCONNECTED -> Toast.makeText(context,
                    "USB disconnected",
                    Toast.LENGTH_SHORT).show()
                ACTION_USB_NOT_SUPPORTED -> Toast.makeText(context,
                    "USB device not supported",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var usbService: UsbConnection? = null
    private var mHandler: MyHandler? = null
    private var displayData: TextView? = null
    private var editTextData: EditText? = null

    private val usbConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
            usbService = (arg1 as UsbConnection.UsbBinder).getService()
            usbService?.setHandler(mHandler)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            usbService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mHandler = MyHandler(this)
        displayData = findViewById<TextView>(R.id.textData)
        editTextData = findViewById<EditText>(R.id.editTextCommand)

        val sendButton = findViewById<Button>(R.id.buttonSend)
        sendButton.setOnClickListener {
            if (editTextData?.text.toString() != "") {
                val data: String = editTextData?.text.toString()
                if (usbService != null) { // if UsbService was correctly binded, Send data
                    usbService!!.write(data.toByteArray())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setFilters() // Start listening notifications from UsbService
        startService(UsbConnection::class.java,
            usbConnection,
            null) // Start UsbService(if it was not started before) and Bind it
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    private fun startService(service: Class<*>, serviceConnection: ServiceConnection, extras: Bundle? ) {
        if (!UsbConnection.SERVICE_CONNECTED) {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet()
                for (key in keys) {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, BIND_AUTO_CREATE)
    }


    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION_GRANTED)
        filter.addAction(ACTION_NO_USB)
        filter.addAction(ACTION_USB_DISCONNECTED)
        filter.addAction(ACTION_USB_NOT_SUPPORTED)
        filter.addAction(ACTION_USB_PERMISSION_NOT_GRANTED)
        registerReceiver(mUsbReceiver, filter)
    }


    /*
     * This handler will be passed to UsbService. Data received from serial port is displayed through this handler
     */
    private class MyHandler(activity: MainActivity) : Handler() {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_FROM_SERIAL_PORT -> {
                    val data = msg.obj as String
                    mActivity.get()!!.displayData?.append(data)
                }
            }
        }

    }
}