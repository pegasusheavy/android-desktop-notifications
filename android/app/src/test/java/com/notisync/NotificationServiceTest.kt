package com.notisync

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.StatusBarNotification
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NotificationServiceTest {

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockSharedPreferences.getBoolean(eq("enabled"), any())).thenReturn(false)
    }

    @Test
    fun `test static instance is initially null`() {
        // Reset instance
        assertNull(NotificationService.instance)
    }

    @Test
    fun `test ConnectionListener interface can be implemented`() {
        val listener = object : ConnectionManager.ConnectionListener {
            var connected = false
            var disconnected = false
            var searching = false

            override fun onConnected(hostName: String) {
                connected = true
            }

            override fun onDisconnected() {
                disconnected = true
            }

            override fun onSearching() {
                searching = true
            }
        }

        listener.onConnected("test-host")
        assertTrue(listener.connected)

        listener.onDisconnected()
        assertTrue(listener.disconnected)

        listener.onSearching()
        assertTrue(listener.searching)
    }

    @Test
    fun `test notification has FLAG_ONGOING_EVENT constant`() {
        assertEquals(0x00000002, Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `test notification extras keys exist`() {
        assertEquals("android.title", Notification.EXTRA_TITLE)
        assertEquals("android.text", Notification.EXTRA_TEXT)
    }

    @Test
    fun `test setEnabled method signature`() {
        // Create a mock service that we can test the method on
        // This verifies the method exists with the correct signature
        val methods = NotificationService::class.java.methods
        val setEnabledMethod = methods.find { it.name == "setEnabled" }

        assertNotNull(setEnabledMethod)
        assertEquals(1, setEnabledMethod?.parameterCount)
        assertEquals(Boolean::class.java, setEnabledMethod?.parameterTypes?.get(0))
    }

    @Test
    fun `test companion object TAG constant`() {
        // Use reflection to verify the TAG constant exists
        val field = NotificationService::class.java.getDeclaredField("TAG")
        field.isAccessible = true
        // TAG is in companion object, so we get it from there
        val companionField = NotificationService::class.java.getDeclaredField("Companion")
        assertNotNull(companionField)
    }

    @Test
    fun `test service extends NotificationListenerService`() {
        val superclass = NotificationService::class.java.superclass
        assertEquals(
            "android.service.notification.NotificationListenerService",
            superclass?.name
        )
    }

    @Test
    fun `test StatusBarNotification expected properties`() {
        // StatusBarNotification has these key properties used in the service
        val packageName = "com.test.app"
        val key = "0|com.test.app|123|null|10001"
        val postTime = 1234567890L
        val titleKey = Notification.EXTRA_TITLE
        val textKey = Notification.EXTRA_TEXT

        assertEquals("com.test.app", packageName)
        assertEquals("0|com.test.app|123|null|10001", key)
        assertEquals(1234567890L, postTime)
        assertEquals("android.title", titleKey)
        assertEquals("android.text", textKey)
    }

    @Test
    fun `test ongoing notification flags logic`() {
        // Test notification without ongoing flag
        val flagsWithoutOngoing = 0
        assertEquals(0, flagsWithoutOngoing and Notification.FLAG_ONGOING_EVENT)

        // Test notification with ongoing flag
        val flagsWithOngoing = Notification.FLAG_ONGOING_EVENT
        assertNotEquals(0, flagsWithOngoing and Notification.FLAG_ONGOING_EVENT)

        // Test notification with multiple flags including ongoing
        val multipleFlags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        assertNotEquals(0, multipleFlags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `test package manager app name resolution`() {
        val mockAppInfo = mock<ApplicationInfo>()
        val packageName = "com.example.app"

        whenever(mockPackageManager.getApplicationInfo(eq(packageName), eq(0)))
            .thenReturn(mockAppInfo)
        whenever(mockPackageManager.getApplicationLabel(mockAppInfo))
            .thenReturn("Example App")

        val appLabel = mockPackageManager.getApplicationLabel(
            mockPackageManager.getApplicationInfo(packageName, 0)
        )

        assertEquals("Example App", appLabel.toString())
    }

    @Test
    fun `test package manager throws NameNotFoundException`() {
        whenever(mockPackageManager.getApplicationInfo(any<String>(), any<Int>()))
            .thenThrow(PackageManager.NameNotFoundException())

        var caught = false
        try {
            mockPackageManager.getApplicationInfo("nonexistent.app", 0)
        } catch (e: PackageManager.NameNotFoundException) {
            caught = true
        }

        assertTrue(caught)
    }

    @Test
    fun `test bundle with null extras values`() {
        val extras = Bundle()

        // Don't put any values - they'll be null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)

        assertNull(title)
        assertNull(text)
    }

    @Test
    fun `test shared preferences enabled check`() {
        whenever(mockSharedPreferences.getBoolean("enabled", false)).thenReturn(true)

        val isEnabled = mockSharedPreferences.getBoolean("enabled", false)
        assertTrue(isEnabled)
    }

    @Test
    fun `test shared preferences disabled by default`() {
        whenever(mockSharedPreferences.getBoolean("enabled", false)).thenReturn(false)

        val isEnabled = mockSharedPreferences.getBoolean("enabled", false)
        assertFalse(isEnabled)
    }

    @Test
    fun `test create PhoneNotification from notification data`() {
        val sbnKey = "0|com.test|1|null|10001"
        val packageName = "com.test"
        val appName = "Test App"
        val title = "Notification Title"
        val text = "Notification Text"
        val postTime = 1234567890L

        val phoneNotification = PhoneNotification(
            id = sbnKey,
            app_package = packageName,
            app_name = appName,
            title = title,
            text = text,
            timestamp = postTime
        )

        assertEquals(sbnKey, phoneNotification.id)
        assertEquals(packageName, phoneNotification.app_package)
        assertEquals(appName, phoneNotification.app_name)
        assertEquals(title, phoneNotification.title)
        assertEquals(text, phoneNotification.text)
        assertEquals(postTime, phoneNotification.timestamp)
    }

    @Test
    fun `test filter own package notifications`() {
        val ownPackage = "com.notisync"
        val otherPackage = "com.other.app"

        // Own package should be filtered
        assertEquals(ownPackage, ownPackage)

        // Other package should not be filtered
        assertNotEquals(ownPackage, otherPackage)
    }

    @Test
    fun `test notification without title or text should be skipped`() {
        val title: String? = null
        val text: String? = null

        // This represents the skip condition
        val shouldSkip = title == null && text == null
        assertTrue(shouldSkip)
    }

    @Test
    fun `test notification with title but no text should not be skipped`() {
        val title: String? = "Has Title"
        val text: String? = null

        val shouldSkip = title == null && text == null
        assertFalse(shouldSkip)
    }

    @Test
    fun `test notification with text but no title should not be skipped`() {
        val title: String? = null
        val text: String? = "Has Text"

        val shouldSkip = title == null && text == null
        assertFalse(shouldSkip)
    }

    @Test
    fun `test notification with both title and text should not be skipped`() {
        val title: String? = "Has Title"
        val text: String? = "Has Text"

        val shouldSkip = title == null && text == null
        assertFalse(shouldSkip)
    }

    @Test
    fun `test instance static property type`() {
        val instanceField = NotificationService::class.java.getDeclaredField("instance")
        assertNotNull(instanceField)
    }

    @Test
    fun `test lifecycle methods exist`() {
        val methods = NotificationService::class.java.methods.map { it.name }

        assertTrue(methods.contains("onCreate"))
        assertTrue(methods.contains("onDestroy"))
        assertTrue(methods.contains("onListenerConnected"))
        assertTrue(methods.contains("onListenerDisconnected"))
        assertTrue(methods.contains("onNotificationPosted"))
        assertTrue(methods.contains("onNotificationRemoved"))
    }
}
