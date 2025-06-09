package org.dhis2.usescases.eventsWithoutRegistration.eventCapture.temprecCoder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import timber.log.Timber

class PermissionManager(private val context: Context) {

    interface PermissionCallback {
        fun onAllPermissionsGranted()
        fun onPermissionsDenied(deniedPermissions: List<String>)
    }

    companion object {
        private val BLUETOOTH_PERMISSIONS_PRE_S = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        private val BLUETOOTH_PERMISSIONS_S_PLUS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun checkBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BLUETOOTH_PERMISSIONS_S_PLUS
        } else {
            BLUETOOTH_PERMISSIONS_PRE_S
        }
    }

    fun hasAllPermissions(permissions: Array<String>): Boolean {
        return permissions.all { isPermissionGranted(it) }
    }

    fun requestPermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>,
        callback: PermissionCallback
    ) {
        val neededPermissions = getNeededPermissions(permissions)

        when {
            neededPermissions.isEmpty() -> {
                Timber.d("All permissions already granted")
                callback.onAllPermissionsGranted()
            }
            isFromBackground(context) -> {
                Timber.w("Permission request attempted from background")
                callback.onPermissionsDenied(neededPermissions.toList())
            }
            else -> {
                Timber.d("Requesting permissions: ${neededPermissions.joinToString()}")
                launcher.launch(neededPermissions)
            }
        }
    }

    private fun getNeededPermissions(permissions: Array<String>): Array<String> {
        return permissions.filterNot { isPermissionGranted(it) }.toTypedArray()
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isFromBackground(context: Context): Boolean {
        return !context.isAppOnForeground()
    }
}

// Extension function to check if app is in foreground
fun Context.isAppOnForeground(): Boolean {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
    val appProcesses = activityManager?.runningAppProcesses ?: return false
    val packageName = packageName

    for (processInfo in appProcesses) {
        if (processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
            processInfo.processName == packageName) {
            return true
        }
    }
    return false
}