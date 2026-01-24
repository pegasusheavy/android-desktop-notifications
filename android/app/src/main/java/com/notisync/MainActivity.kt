package com.notisync

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById(R.id.enableSwitch)
        permissionButton = findViewById(R.id.permissionButton)

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", isChecked).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        val hasPermission = isNotificationListenerEnabled()
        permissionButton.visibility = if (hasPermission) View.GONE else View.VISIBLE
        enableSwitch.isEnabled = hasPermission

        val prefs = getSharedPreferences("notisync", MODE_PRIVATE)
        enableSwitch.isChecked = prefs.getBoolean("enabled", false)

        // Update status based on connection state
        val isConnected = prefs.getBoolean("connected", false)
        if (isConnected) {
            statusIndicator.setBackgroundResource(R.drawable.circle_green)
            statusText.text = "Connected"
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_red)
            statusText.text = if (hasPermission) "Searching..." else "Disconnected"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val componentName = ComponentName(this, NotificationService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(componentName.flattenToString()) == true
    }
}
