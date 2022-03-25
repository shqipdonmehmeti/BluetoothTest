package com.example.bluetoothtest.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.bluetoothtest.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import java.lang.Exception
import java.util.*
import kotlin.math.log


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var launcher : ActivityResultLauncher<Intent>
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>
    private  var bluetoothAdapter : BluetoothAdapter? = null
    private lateinit var bluetoothManager: BluetoothManager
    private val MY_UUID : UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var connectThread : ConnectThread
    private lateinit var connectedThread: ConnectedThread

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        var counter = 0
        launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                handleConnection()
                Log.d("TAG", "data: $data")
            }
        }
        requestMultiplePermissions  = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach {
                    if (it.value == true) {
                        counter++
                    }
                    Log.d("test006", "${it.key} = ${it.value}")
                }
            if (counter == 2) {
                handleConnection()
            } else {
                Log.d("TAG", "permission not granted: ")
            }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConnect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestMultiplePermissions.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT))
            }
//            handleConnection()
        }
    }



    private fun handleConnection() {
         bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
         bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Log.d("TAG", "Device does not support bluetooth: ")
        } else {
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                launcher.launch(enableBtIntent)
            }
            else {
                showPairedList()
                Log.d("TAG", "Bluetooth already on: ")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedList() {
        val arrayAdapter: ArrayAdapter<String> = ArrayAdapter(requireContext(),android.R.layout.simple_list_item_1)
        val pairedDevices : Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices

        pairedDevices.forEach {
            arrayAdapter.add(it.name.plus("\n").plus(it.address))
        }
        val builder : AlertDialog.Builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Pair with one device")
        builder.setAdapter(arrayAdapter) {dialog: DialogInterface?, which: Int ->
            val clickedItem = arrayAdapter.getItem(which)
            val clickedItemMacAddress = clickedItem!!.substring(clickedItem.length - 17)
            val device : BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(clickedItemMacAddress)
            Log.d("TAG", "ClickedItem: $clickedItem ")
            Log.d("TAG", "ClickedItemMacAddress: $clickedItemMacAddress ")
            connectThread = ConnectThread(device)
            connectThread.start()
        }
        builder.show()

    }








    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                try {
                    socket.connect()
                    connectedThread = ConnectedThread(socket)
                    connectedThread.start()
                    Log.d("TAG", "Here it goes if success: ")
                } catch (e : Exception) {
                    Log.d("TAG", "exception: ${e.message}")
                }

                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
//                manageMyConnectedSocket(socket)
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
                Log.d("TAG", "cancel: ")
            } catch (e: IOException) {
                Log.e("TAG", "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val inputStream: DataInputStream = DataInputStream(mmInStream)
        private val inputStreamReader: InputStreamReader = InputStreamReader(inputStream)
        private val bufferedReader: BufferedReader = BufferedReader(inputStreamReader)
        private val mmBuffer: ByteArray = ByteArray(4096) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()
            var line : String? = null

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
               try {
                    line = bufferedReader.readLine()
                   Log.d("TAG", "Line: $line")
                }
                catch (e: IOException) {
                    Log.d("TAG", "Input stream was disconnected", e)
                    break
                }
                // Send the obtained bytes to the UI activity.
//                val readMsg = handler.obtainMessage(
//                    MESSAGE_READ, numBytes, -1,
//                    mmBuffer)
//                readMsg.sendToTarget()
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e("TAG", "Error occurred when sending data", e)

                // Send a failure message back to the activity.
//                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
//                writeErrorMsg.data = bundle
//                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
//            val writtenMsg = handler.obtainMessage(
//                MESSAGE_WRITE, -1, -1, mmBuffer)
//            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e("TAG", "Could not close the connect socket", e)
            }
        }
    }


    override fun onDestroyView() {
        Log.d("TAG", "onDestroyView: ")
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        Log.d("TAG", "onDestroy: ")
        super.onDestroy()
        connectThread.cancel()
    }
}


//    @SuppressLint("MissingPermission")
//    private fun connectThread(device: BluetoothDevice) {
//        val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
//            device.createRfcommSocketToServiceRecord(MY_UUID)
//        }
//        val job = lifecycleScope.launch {
//            bluetoothAdapter?.cancelDiscovery()
//            mmSocket?.let { socket ->
//                // Connect to the remote device through the socket. This call blocks
//                // until it succeeds or throws an exception.
//                try {
//                    socket.connect()
////                    connectedThread = ConnectedThread(socket)
////                    connectedThread.start()
//                    Log.d("TAG", "Here it goes if success: ")
//                } catch (e : Exception) {
//                    Log.d("TAG", "exception: ${e.message}")
//                }
//
//                // The connection attempt succeeded. Perform work associated with
//                // the connection in a separate thread.
////                manageMyConnectedSocket(socket)
//            }
//        }
//
//        fun cancel() {
//            try {
//                mmSocket?.close()
//                Log.d("TAG", "cancel: ")
//            } catch (e: IOException) {
//                Log.e("TAG", "Could not close the client socket", e)
//            }
//        }
//        }