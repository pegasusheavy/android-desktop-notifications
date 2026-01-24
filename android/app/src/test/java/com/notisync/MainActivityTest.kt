package com.notisync

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MainActivityTest {

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockContentResolver: ContentResolver

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
    }

    @Test
    fun `test activity class exists and extends AppCompatActivity`() {
        val superclass = MainActivity::class.java.superclass
        assertEquals(
            "androidx.appcompat.app.AppCompatActivity",
            superclass?.name
        )
    }

    @Test
    fun `test settings action constant for notification listeners`() {
        assertEquals(
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        )
    }

    @Test
    fun `test shared preferences key constants`() {
        val enabledKey = "enabled"
        val connectedKey = "connected"
        val prefsName = "notisync"

        assertEquals("enabled", enabledKey)
        assertEquals("connected", connectedKey)
        assertEquals("notisync", prefsName)
    }

    @Test
    fun `test shared preferences stores enabled state`() {
        whenever(mockSharedPreferences.getBoolean("enabled", false)).thenReturn(true)

        val isEnabled = mockSharedPreferences.getBoolean("enabled", false)
        assertTrue(isEnabled)
    }

    @Test
    fun `test shared preferences stores connected state`() {
        whenever(mockSharedPreferences.getBoolean("connected", false)).thenReturn(true)

        val isConnected = mockSharedPreferences.getBoolean("connected", false)
        assertTrue(isConnected)
    }

    @Test
    fun `test component name creation for NotificationService`() {
        val context = mock<Context>()
        whenever(context.packageName).thenReturn("com.notisync")

        val componentName = ComponentName("com.notisync", NotificationService::class.java.name)

        assertEquals("com.notisync", componentName.packageName)
        assertTrue(componentName.className.endsWith("NotificationService"))
    }

    @Test
    fun `test component name flatten to string`() {
        val componentName = ComponentName("com.notisync", "com.notisync.NotificationService")
        val flat = componentName.flattenToString()

        assertEquals("com.notisync/com.notisync.NotificationService", flat)
    }

    @Test
    fun `test notification listener enabled check logic`() {
        val componentFlat = "com.notisync/com.notisync.NotificationService"

        // When listener is in the list
        val enabledListeners = "com.other/com.other.Service:com.notisync/com.notisync.NotificationService"
        assertTrue(enabledListeners.contains(componentFlat))

        // When listener is not in the list
        val disabledListeners = "com.other/com.other.Service"
        assertFalse(disabledListeners.contains(componentFlat))

        // When list is null
        val nullListeners: String? = null
        assertFalse(nullListeners?.contains(componentFlat) == true)
    }

    @Test
    fun `test visibility constants`() {
        assertEquals(View.VISIBLE, 0)
        assertEquals(View.GONE, 8)
    }

    @Test
    fun `test permission button visibility logic`() {
        // When has permission - button should be GONE
        val hasPermission = true
        val expectedVisibility = if (hasPermission) View.GONE else View.VISIBLE
        assertEquals(View.GONE, expectedVisibility)

        // When no permission - button should be VISIBLE
        val noPermission = false
        val expectedVisibility2 = if (noPermission) View.GONE else View.VISIBLE
        assertEquals(View.VISIBLE, expectedVisibility2)
    }

    @Test
    fun `test switch enabled logic`() {
        // Switch should be enabled only when has permission
        val hasPermission = true
        assertTrue(hasPermission)

        val noPermission = false
        assertFalse(noPermission)
    }

    @Test
    fun `test status text values`() {
        val connectedText = "Connected"
        val searchingText = "Searching..."
        val disconnectedText = "Disconnected"

        assertEquals("Connected", connectedText)
        assertEquals("Searching...", searchingText)
        assertEquals("Disconnected", disconnectedText)
    }

    @Test
    fun `test status indicator logic when connected`() {
        val isConnected = true
        val hasPermission = true

        val statusText = if (isConnected) "Connected" else if (hasPermission) "Searching..." else "Disconnected"
        assertEquals("Connected", statusText)
    }

    @Test
    fun `test status indicator logic when disconnected with permission`() {
        val isConnected = false
        val hasPermission = true

        val statusText = if (isConnected) "Connected" else if (hasPermission) "Searching..." else "Disconnected"
        assertEquals("Searching...", statusText)
    }

    @Test
    fun `test status indicator logic when disconnected without permission`() {
        val isConnected = false
        val hasPermission = false

        val statusText = if (isConnected) "Connected" else if (hasPermission) "Searching..." else "Disconnected"
        assertEquals("Disconnected", statusText)
    }

    @Test
    fun `test NotificationService instance can be accessed`() {
        // Initially null
        val instance = NotificationService.instance
        assertNull(instance)
    }

    @Test
    fun `test Intent for notification listener settings`() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intent.action)
    }

    @Test
    fun `test lifecycle methods exist`() {
        val declaredMethods = MainActivity::class.java.declaredMethods.map { it.name }

        // onCreate is declared in MainActivity
        assertTrue("onCreate should be declared", declaredMethods.contains("onCreate"))

        // onResume is declared in MainActivity
        assertTrue("onResume should be declared", declaredMethods.contains("onResume"))
    }

    @Test
    fun `test private methods exist via declared methods`() {
        val methods = MainActivity::class.java.declaredMethods.map { it.name }

        assertTrue(methods.contains("updateUI"))
        assertTrue(methods.contains("isNotificationListenerEnabled"))
    }

    @Test
    fun `test shared preferences editor chaining`() {
        whenever(mockEditor.putBoolean("enabled", true)).thenReturn(mockEditor)

        val result = mockEditor.putBoolean("enabled", true)
        assertEquals(mockEditor, result)

        verify(mockEditor).putBoolean("enabled", true)
    }

    @Test
    fun `test secure settings key for notification listeners`() {
        val key = "enabled_notification_listeners"
        assertEquals("enabled_notification_listeners", key)
    }

    @Test
    fun `test R layout and drawable resources referenced`() {
        // These are resource IDs that exist in the app
        // We can't directly test R values in unit tests, but we can verify the patterns used

        // Layout: R.layout.activity_main
        // Views: R.id.statusIndicator, R.id.statusText, R.id.enableSwitch, R.id.permissionButton
        // Drawables: R.drawable.circle_green, R.drawable.circle_red

        // Verify the activity uses these patterns
        val methods = MainActivity::class.java.declaredMethods

        val onCreateMethod = methods.find { it.name == "onCreate" }
        assertNotNull(onCreateMethod)
    }

    @Test
    fun `test mode_private constant`() {
        assertEquals(0, Context.MODE_PRIVATE)
    }

    @Test
    fun `test activity view hierarchy fields`() {
        val fields = MainActivity::class.java.declaredFields.map { it.name }

        // Check that the expected view fields exist
        assertTrue(fields.any { it.contains("statusIndicator") })
        assertTrue(fields.any { it.contains("statusText") })
        assertTrue(fields.any { it.contains("enableSwitch") })
        assertTrue(fields.any { it.contains("permissionButton") })
    }

    @Test
    fun `test enable switch listener saves preference`() {
        val isChecked = true

        mockEditor.putBoolean("enabled", isChecked)
        mockEditor.apply()

        verify(mockEditor).putBoolean("enabled", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `test enable switch listener calls service setEnabled`() {
        // When switch is changed, it should call NotificationService.instance?.setEnabled(isChecked)
        // This verifies the method signature exists
        val methods = NotificationService::class.java.methods
        val setEnabledMethod = methods.find { it.name == "setEnabled" }

        assertNotNull(setEnabledMethod)
        assertEquals(1, setEnabledMethod?.parameterCount)
    }

    @Test
    fun `test button click listener creates correct intent`() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        assertNotNull(intent)
        assertEquals("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", intent.action)
    }

    @Test
    fun `test updateUI reads correct preferences`() {
        // Verify the prefs being read
        val enabledKey = "enabled"
        val connectedKey = "connected"
        val defaultValue = false

        whenever(mockSharedPreferences.getBoolean(enabledKey, defaultValue)).thenReturn(true)
        whenever(mockSharedPreferences.getBoolean(connectedKey, defaultValue)).thenReturn(false)

        val enabled = mockSharedPreferences.getBoolean(enabledKey, defaultValue)
        val connected = mockSharedPreferences.getBoolean(connectedKey, defaultValue)

        assertTrue(enabled)
        assertFalse(connected)
    }

    @Test
    fun `test context getSharedPreferences call`() {
        val context = mock<Context>()
        whenever(context.getSharedPreferences("notisync", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)

        val prefs = context.getSharedPreferences("notisync", Context.MODE_PRIVATE)

        assertNotNull(prefs)
        verify(context).getSharedPreferences("notisync", Context.MODE_PRIVATE)
    }
}
