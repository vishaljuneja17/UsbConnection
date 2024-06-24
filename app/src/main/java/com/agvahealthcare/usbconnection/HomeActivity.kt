package com.agvahealthcare.usbconnection

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.ACTION_NO_USB
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.ACTION_USB_DISCONNECTED
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.ACTION_USB_NOT_SUPPORTED
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.ACTION_USB_PERMISSION_GRANTED
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.ACTION_USB_PERMISSION_NOT_GRANTED
import com.agvahealthcare.usbconnection.UsbCommunication.Companion.SYNC_READ
import java.lang.ref.WeakReference

class HomeActivity : AppCompatActivity() {

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
                ACTION_NO_USB -> Toast.makeText(context,
                    "No USB connected",
                    Toast.LENGTH_SHORT).show()
                ACTION_USB_DISCONNECTED -> Toast.makeText(context,
                    "USB disconnected",
                    Toast.LENGTH_SHORT).show()
                ACTION_USB_NOT_SUPPORTED -> Toast.makeText(context,
                    "USB device not supported",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var usbService: UsbCommunication? = null
    private var displayDataUsb1: TextView? = null
    private var displayDataUsb2: TextView? = null
    private var editTextDataUsb1: EditText? = null
    private var editTextDataUsb2: EditText? = null
    private var cmdButton1 : Button?= null
    private var cmdButton2 : Button?= null
    private var mHandler: MyHandler? = null


    private val usbConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
            usbService = (arg1 as UsbCommunication.UsbBinder).getService()
            usbService?.setHandler(mHandler)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            usbService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        displayDataUsb1 = findViewById(R.id.textDataUsb1)
        displayDataUsb2 = findViewById(R.id.textDataUsb2)
        editTextDataUsb1 = findViewById(R.id.editTextCommandUsb1)
        editTextDataUsb2 = findViewById(R.id.editTextCommandUsb2)

        cmdButton1 = findViewById(R.id.buttonCmd1)
        cmdButton2 = findViewById(R.id.buttonCmd2)

        cmdButton1?.setOnClickListener { v: View? ->
            val data: ByteArray = editTextDataUsb1?.text.toString().toByteArray()
            usbService?.write(data, 0)
        }
        cmdButton2?.setOnClickListener { v: View? ->
            val data: ByteArray = editTextDataUsb2?.text.toString().toByteArray()
            usbService?.write(data, 1)
        }
        mHandler = MyHandler(this)
    }

    override fun onResume() {
        super.onResume()
        setFilters() // Start listening notifications from UsbService
        startService(UsbCommunication::class.java, usbConnection, null) // Start UsbService(if it was not started before) and Bind it
    }


    override fun onPause() {
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    private fun startService(
        service: Class<*>,
        serviceConnection: ServiceConnection,
        extras: Bundle?,
    ) {
        if (!UsbCommunication.SERVICE_CONNECTED) {
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

    private class MyHandler(activity: HomeActivity) : Handler() {

        private val mActivity: WeakReference<HomeActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SYNC_READ -> {
                    val buffer = msg.obj as String
                    if (msg.arg1 == 0) {
                        mActivity.get()?.displayDataUsb1?.append(buffer)
                    } else if (msg.arg1 == 1) {
                        mActivity.get()?.displayDataUsb2?.append(buffer)
                    }
                }
            }
        }

    }
}