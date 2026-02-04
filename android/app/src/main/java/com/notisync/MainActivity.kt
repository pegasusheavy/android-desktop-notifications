package com.notisync

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var statusSubtext: TextView
    private lateinit var enableSwitch: MaterialSwitch
    private lateinit var permissionButton: MaterialButton
    private lateinit var batteryButton: MaterialButton

    private val stateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        statusSubtext = findViewById(R.id.statusSubtext)
        enableSwitch = findViewById(R.id.enableSwitch)
        permissionButton = findViewById(R.id.permissionButton)
        batteryButton = findViewById(R.id.batteryButton)

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        batteryButton.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
            NotificationService.instance?.setEnabled(isChecked)
            updateUI()
        }

        // Register for state change broadcasts
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(stateChangeReceiver, IntentFilter("com.notisync.STATE_CHANGED"))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateChangeReceiver)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasPermission = isNotificationListenerEnabled()
        val hasBatteryExemption = isBatteryOptimizationDisabled()
        
        permissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
        batteryButton.visibility = if (hasBatteryExemption || !hasPermission) View.GONE else View.VISIBLE
        enableSwitch.isEnabled = hasPermission

        val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        enableSwitch.isChecked = isEnabled

        // Update status based on connection state
        val isConnected = prefs.getBoolean("connected", false)
        val isSearching = prefs.getBoolean("searching", false)

        when {
            !hasPermission -> {
                statusIndicator.setBackgroundResource(R.drawable.circle_red)
                statusText.text = "Permission Required"
                statusSubtext.text = "Grant notification access to continue"
            }
            isConnected -> {
                statusIndicator.setBackgroundResource(R.drawable.circle_green)
                statusText.text = "Connected"
                statusSubtext.text = "Notifications will sync to your desktop"
            }
            isSearching -> {
                statusIndicator.setBackgroundResource(R.drawable.circle_orange)
                statusText.text = "Searching..."
                statusSubtext.text = "Looking for your desktop on the network"
            }
            isEnabled -> {
                statusIndicator.setBackgroundResource(R.drawable.circle_orange)
                statusText.text = "Searching..."
                statusSubtext.text = "Looking for your desktop on the network"
            }
            else -> {
                statusIndicator.setBackgroundResource(R.drawable.circle_red)
                statusText.text = "Disabled"
                statusSubtext.text = "Enable sync to get started"
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, NotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(componentName.flattenToString()) == true
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    @Suppress("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }
}
