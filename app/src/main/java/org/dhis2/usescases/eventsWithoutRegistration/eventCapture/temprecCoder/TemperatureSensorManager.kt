package org.dhis2.usescases.eventsWithoutRegistration.com.coder.temperec.ul.theme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import timber.log.Timber
import java.util.*

class TemperatureSensorManager(
    private val context: Context,
    private val permissionsLauncher: ActivityResultLauncher<String>,
    private val bluetoothLauncher: ActivityResultLauncher<Intent>,
    private val temperatureCallback: TemperatureCallback
) {
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
    private var deviceFound = false
    private var latestTemperature: Float? = null

    // UUIDs for the custom BLE service and characteristic
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val CHARACTERISTIC_UUID = UUID.fromString("abcd1234-ab12-cd34-ef56-abcdef123456")

    /** Public method to start scanning */
    fun startScanning() {
        requestPermissions()
    }

    /** Request necessary permissions */
    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val notGranted = permissions.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            checkBluetoothEnabled()
        } else {
            permissionsLauncher.launch(permissions.toString())
        }
    }

    /** Check if Bluetooth is enabled */
    fun checkBluetoothEnabled() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothLauncher.launch(enableBtIntent)
            } else {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                startScan()
            }
        }
    }

    /** Actually start scanning for the device */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        deviceFound = false

        scanner?.startScan(scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!deviceFound) {
                scanner?.stopScan(scanCallback)
                Timber.tag("TemperatureSensorManage").e("Device not found.")
            }
        }, 10000) // 10 seconds timeout
    }

    /** Scan callback for BLE device discovery */
    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name

            if (name == "ESP32-Thermo") { // <-- Your BLE device name
                deviceFound = true
                scanner?.stopScan(this)
                connectToDevice(device)
            }
        }
    }

    /** Connect to the BLE device */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    /** GATT callback for connection and service discovery */
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt?.close()
                bluetoothGatt = null
                Timber.tag("TemperatureSensorManage").e("Disconnected from GATT server.")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor?.let { desc ->
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(desc)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val valueString = characteristic.getStringValue(0)
            val temp = valueString.toFloatOrNull()
            temp?.let {
                latestTemperature = it
                temperatureCallback.onTemperatureReceived(it)
            }
        }
    }

    /** Return latest received temperature */
    fun getTemperature(): Float? {
        return latestTemperature
    }

    /** Stop and close Bluetooth connection */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopScanning() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    /** Interface to send temperature data back */
    interface TemperatureCallback {
        fun onTemperatureReceived(temperature: Float)
    }
}