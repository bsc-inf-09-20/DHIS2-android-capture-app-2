import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.temprecCoder.PermissionManager
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.*

@SuppressLint("MissingPermission")
class TemperatureSensorManager private constructor(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val bluetoothIntentLauncher: (Intent) -> Unit,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val uiMessageHandler: (message: String, isError: Boolean) -> Unit
) {
    companion object {
        private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
        private val CHARACTERISTIC_UUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val TARGET_DEVICE_NAME = "ESP32-Thermo"
        internal const val MAX_CONNECTION_ATTEMPTS = 3
        private const val TAG = "BLEManager"
        private const val RECONNECT_DELAY_MS = 1000L

        fun create(
            context: Context,
            permissionManager: PermissionManager,
            bluetoothIntentLauncher: (Intent) -> Unit,
            permissionLauncher: ActivityResultLauncher<Array<String>>,
            uiMessageHandler: (message: String, isError: Boolean) -> Unit
        ): TemperatureSensorManager? {
            return if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                debugLog("Bluetooth LE supported, creating manager")
                TemperatureSensorManager(
                    context,
                    permissionManager,
                    bluetoothIntentLauncher,
                    permissionLauncher,
                    uiMessageHandler
                )
            } else {
                showCenteredMessage(context, "Bluetooth LE not supported", true)
                null
            }
        }

        private fun debugLog(message: String) {
            Timber.tag(TAG).d(message)
            Log.d(TAG, message)
        }

        private fun debugError(message: String) {
            Timber.tag(TAG).e(message)
            Log.e(TAG, message)
        }

        private fun showCenteredMessage(context: Context, message: String, isError: Boolean) {
            Handler(Looper.getMainLooper()).post {
                val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
                val view = toast.view
                view?.setBackgroundColor(if (isError) Color.RED else Color.BLUE)
                val text = view?.findViewById<TextView>(android.R.id.message)
                text?.setTextColor(Color.WHITE)
                text?.textSize = 18f
                toast.duration = Toast.LENGTH_LONG
                toast.show()
            }
        }
    }

    // Bluetooth components
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val scanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    // State management
    private enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
    }

    private val handler = Handler(Looper.getMainLooper())
    private var connectionState = ConnectionState.DISCONNECTED
    private var isScanning = false
    private var connectionAttempts = 0
    internal var latestTemperature: Float? = null
    private var targetDevice: BluetoothDevice? = null
    private var stateChangeListener: StateChangeListener? = null

    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.M)
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.device?.takeIf { it.name == TARGET_DEVICE_NAME }?.let { device ->
               // debugLog("Found target device: ${device.name}")
                targetDevice = device
                stopScan()
                connectToDevice(device)
                showCenteredMessage(context, "Device found: ${device.name}", false)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                else -> "Unknown error ($errorCode)"
            }
            handleError("Scan failed: $errorMsg", true)
            cleanupAfterScanFailure()
            stateChangeListener?.onScanFailed(errorCode)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresApi(Build.VERSION_CODES.M)
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                       // debugLog("Connected to $deviceAddress")
                        connectionState = ConnectionState.CONNECTED
                        connectionAttempts = 0
                        handler.removeCallbacksAndMessages(null)
                        stateChangeListener?.onDeviceConnected(gatt.device)
                        showCenteredMessage(context, "Connected to device", false)

                        if (!gatt.discoverServices()) {
                            handleError("Failed to start service discovery", true)
                            disconnect()
                        }
                    } else {
                        handleError("Connection failed with status $status", true)
                        attemptReconnectOrCleanup()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                   // debugLog("Disconnected from $deviceAddress")
                    connectionState = ConnectionState.DISCONNECTED
                    stateChangeListener?.onDeviceDisconnected()
                    showCenteredMessage(context, "Disconnected from device", true)

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        handleError("Disconnected with error status $status", true)
                    }

                    if (status != BluetoothGatt.GATT_SUCCESS && connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                        attemptReconnect()
                    } else {
                        cleanup()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
               // debugLog("Services discovered")
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        setupCharacteristicNotifications(gatt, characteristic)
                        //showCenteredMessage(context, "Service discovered", false)
                    } else {
                        handleError("Temperature characteristic not found", true)
                        disconnect()
                    }
                } else {
                    handleError("Temperature service not found", true)
                    disconnect()
                }
            } else {
                handleError("Service discovery failed: $status", true)
                disconnect()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                try {
                    val tempString = characteristic.getStringValue(0)

                    tempString?.let {
                        val tempValue = it.toFloatOrNull()

                        debugLog("TEMP: "+""+tempValue)

                        if (tempValue != null && tempValue in 20f..45f) {
                            latestTemperature = tempValue
                            stateChangeListener?.onTemperatureUpdate(tempValue)

                            // Save temperature using application context (avoids memory leak)
                            val appContext = context.applicationContext
                            val prefs = MyPreferences(appContext)
                            debugLog("temp:"+""+tempValue)

                            CoroutineScope(Dispatchers.IO).launch {
                                delay(10000) // Delay for 10 seconds
                                prefs.saveTempData(tempValue.toString())
                            }

                            showCenteredMessage(appContext, "Temperature: $tempValue°C", false)
                        } else {
                            showCenteredMessage(context, "Invalid temperature reading", true)
                        }
                    }
                } catch (e: Exception) {
                    handleError("Failed to parse temperature: ${e.message}", false)
                }
            }
        }


        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == CHARACTERISTIC_UUID) {
                try {
                    val tempString = characteristic.getStringValue(0)
                 //   debugLog("Read temperature string: \"$tempString\"")

                    tempString?.let {
                        val tempValue = it.toFloatOrNull()
                        if (tempValue != null && tempValue in 20f..45f) {
                            latestTemperature = tempValue
                            stateChangeListener?.onTemperatureUpdate(tempValue)
                       //     debugLog("Valid temperature read: $tempValue°C")
                            showCenteredMessage(context, "Temperature: $tempValue°C", false)
                        } else {
                    //        debugError("Invalid temperature value: $tempString")
                            showCenteredMessage(context, "Invalid temperature reading", true)
                        }
                    }
                } catch (e: Exception) {
                    handleError("Failed to parse temperature: ${e.message}", false)
                }
            } else {
                handleError("Characteristic read failed: $status", false)
            }
        }
    }

    private fun setupCharacteristicNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            handleError("Failed to setup notifications", true)
            return
        }

        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!gatt.writeDescriptor(descriptor)) {
            handleError("Failed to write descriptor for notifications", true)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        val bluetoothPermissions = permissionManager.checkBluetoothPermissions()
        if (!permissionManager.hasAllPermissions(bluetoothPermissions)) {
            permissionLauncher.launch(bluetoothPermissions)
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            bluetoothIntentLauncher(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (isScanning || connectionState != ConnectionState.DISCONNECTED) {
           // debugLog("Already scanning or connected/connecting")
            return
        }

       // debugLog("Starting BLE scan")
        isScanning = true
        connectionState = ConnectionState.CONNECTING
        stateChangeListener?.onScanStarted()

        // Set scan timeout
        handler.postDelayed({
            if (isScanning) {
                //debugLog("Scan timed out")
                showCenteredMessage(context, "Scan timed out", true)
                stopScan()
             //   handleError("Device not found", true)
                showCenteredMessage(context, "Device not found", true)
            }
        }, SCAN_TIMEOUT_MS)

        // Start scan with high power mode
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        if (isScanning) {
           // debugLog("Stopping BLE scan")
            scanner?.stopScan(scanCallback)
            isScanning = false
            handler.removeCallbacksAndMessages(null)
            stateChangeListener?.onScanStopped()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
      //  debugLog("Attempting to connect to ${device.address}")
        connectionState = ConnectionState.CONNECTING
        stateChangeListener?.onConnectionAttempt(connectionAttempts + 1)

        // Close any existing GATT connection
        bluetoothGatt?.close()
        bluetoothGatt = null

        // Connect with autoConnect=false for faster initial connection
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        when (connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
               // debugLog("")
                showCenteredMessage(context, "Disconnecting from device", true)
                connectionState = ConnectionState.DISCONNECTING
                bluetoothGatt?.disconnect()
            }
            else -> {
              //  debugLog("Not connected, no need to disconnect")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readTemperature() {
        if (connectionState != ConnectionState.CONNECTED) {
         //   handleError("Not connected to device", true)
            return
        }

        bluetoothGatt?.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)?.let {
            if (!bluetoothGatt?.readCharacteristic(it)!!) {
                handleError("Failed to read temperature", false)
            }
        } //?: handleError("Service or characteristic not available", true)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun attemptReconnect() {
        connectionAttempts++
        if (connectionAttempts <= MAX_CONNECTION_ATTEMPTS) {
         //   debugLog("Attempting reconnect ($connectionAttempts/$MAX_CONNECTION_ATTEMPTS)")
            handler.postDelayed({
                targetDevice?.let { connectToDevice(it) }
            }, RECONNECT_DELAY_MS)
        } else {
          //  handleError("Max connection attempts reached", true)
            cleanup()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun attemptReconnectOrCleanup() {
        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
            attemptReconnect()
        } else {
            cleanup()
        }
    }

    private fun cleanup() {
        debugLog("Cleaning up resources")
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        targetDevice = null
        connectionState = ConnectionState.DISCONNECTED
        connectionAttempts = 0
    }

    private fun cleanupAfterScanFailure() {
        isScanning = false
        connectionState = ConnectionState.DISCONNECTED
        handler.removeCallbacksAndMessages(null)
    }

    private fun handleError(message: String, isCritical: Boolean) {
        debugError(message)
        uiMessageHandler(message, isCritical)
        stateChangeListener?.onError(message, isCritical)
    }

    fun setStateChangeListener(listener: StateChangeListener?) {
        stateChangeListener = listener
    }

    fun getLatestTemperature(): Float? = latestTemperature

    interface StateChangeListener {
        fun onScanStarted()
        fun onScanStopped()
        fun onScanFailed(errorCode: Int)
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected()
        fun onTemperatureUpdate(temperature: Float)
        fun onConnectionAttempt(attempt: Int)
        fun onError(message: String, isCritical: Boolean)
    }
}