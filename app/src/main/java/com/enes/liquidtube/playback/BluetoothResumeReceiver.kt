package com.enes.liquidtube.playback

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerEntryPoint {
    fun playerConnection(): PlayerConnection
}

/**
 * A Bluetooth audio device (headphones, speaker, car) connected while LiquidTube wasn't
 * open: pick up where you left off, if you paused recently. Needs the Nearby devices
 * permission (asked for in Settings → Resume Automatically).
 */
class BluetoothResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val audio = runCatching { device?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .getOrDefault(true) // can't tell: let the connection's own checks decide
        if (!audio) return
        // Give the device a moment to become the audio output first.
        val pending = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, PlayerEntryPoint::class.java)
                    .playerConnection()
                    .resumeOnDevice()
            }.onFailure { Log.w("LiquidTube", "Bluetooth resume failed", it) }
            pending.finish()
        }, 2_500)
    }
}
