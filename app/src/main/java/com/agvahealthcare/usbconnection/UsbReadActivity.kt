package com.agvahealthcare.usbconnection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.agvahealthcare.usbconnection.UsbConnect.Companion.ACTION_DATA_AVAILABLE_1
import com.agvahealthcare.usbconnection.UsbConnect.Companion.ACTION_DATA_AVAILABLE_2
import com.agvahealthcare.usbconnection.UsbConnect.Companion.ACTION_USB_PERMISSION_1
import com.agvahealthcare.usbconnection.UsbConnect.Companion.ACTION_USB_PERMISSION_2

class UsbReadActivity : AppCompatActivity() {

    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                    Toast.makeText(context,
                    "USB ATTACH",
                    Toast.LENGTH_SHORT).show()
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    Toast.makeText(context,
                    "USB DETACHED",
                    Toast.LENGTH_SHORT).show()
                ACTION_DATA_AVAILABLE_1 ->
                    dataGetFromUsb1(intent)

                ACTION_DATA_AVAILABLE_2->

                    dataGetFromUsb2(intent)
            }
        }
    }

    private var displayDataUsb1: TextView? = null
    private var displayDataUsb2: TextView? = null
    private var editTextDataUsb1: EditText? = null
    private var editTextDataUsb2: EditText? = null
    private var cmdButton1 : Button?= null
    private var cmdButton2 : Button?= null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        displayDataUsb1 = findViewById(R.id.textDataUsb1)
        displayDataUsb2 = findViewById(R.id.textDataUsb2)
        editTextDataUsb1 = findViewById(R.id.editTextCommandUsb1)
        editTextDataUsb2 = findViewById(R.id.editTextCommandUsb2)

        cmdButton1 = findViewById(R.id.buttonCmd1)
        cmdButton2 = findViewById(R.id.buttonCmd2)

    }

    override fun onResume() {
        super.onResume()
//        setFilter()
        Intent(this, UsbConnect::class.java).also { intent ->
            startService(intent)
        }

    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(ACTION_USB_PERMISSION_1)
        filter.addAction(ACTION_USB_PERMISSION_2)
        registerReceiver(mUsbReceiver, filter)
    }


    override fun onPause() {
        super.onPause()
//        unregisterReceiver(mUsbReceiver)
    }



    private fun dataGetFromUsb1(i: Intent) {


        val dataUsb1 = i.getStringExtra("data")
        Toast.makeText(this, "DATA_GET_USB1$dataUsb1", Toast.LENGTH_SHORT).show()

        Log.e("Usb1"  , dataUsb1!!)

        displayDataUsb1?.append(dataUsb1)

    }

    private fun dataGetFromUsb2(i: Intent) {

        val dataUsb2 = i.getStringExtra("data")
        Toast.makeText(this, "DATA_GET_USB1$dataUsb2", Toast.LENGTH_SHORT).show()

        Log.e("Usb2"  , dataUsb2!!)
        displayDataUsb2?.append(dataUsb2)


    }


}