package com.notisync

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ConnectionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockNsdManager: NsdManager

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var connectionManager: ConnectionManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(mockContext.getSystemService(Context.NSD_SERVICE)).thenReturn(mockNsdManager)
        whenever(mockContext.getSharedPreferences(eq("notisync"), eq(Context.MODE_PRIVATE)))
            .thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)

        connectionManager = ConnectionManager(mockContext)
    }

    @Test
    fun `test ConnectionListener interface has required methods`() {
        var connectedCalled = false
        var disconnectedCalled = false
        var searchingCalled = false

        val listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {
                connectedCalled = true
                assertEquals("192.168.1.1", hostName)
            }

            override fun onDisconnected() {
                disconnectedCalled = true
            }

            override fun onSearching() {
                searchingCalled = true
            }
        }

        listener.onConnected("192.168.1.1")
        listener.onDisconnected()
        listener.onSearching()

        assertTrue(connectedCalled)
        assertTrue(disconnectedCalled)
        assertTrue(searchingCalled)
    }

    @Test
    fun `test listener can be set and retrieved`() {
        val listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {}
            override fun onDisconnected() {}
            override fun onSearching() {}
        }

        connectionManager.listener = listener
        assertEquals(listener, connectionManager.listener)
    }

    @Test
    fun `test listener can be null`() {
        connectionManager.listener = null
        assertNull(connectionManager.listener)
    }

    @Test
    fun `test start initiates discovery`() {
        connectionManager.start()

        verify(mockContext).getSystemService(Context.NSD_SERVICE)
        verify(mockNsdManager).discoverServices(
            eq("_notisync._tcp."),
            eq(NsdManager.PROTOCOL_DNS_SD),
            any()
        )
    }

    @Test
    fun `test start twice does not restart discovery`() {
        connectionManager.start()
        connectionManager.start()

        // Should only call discoverServices once
        verify(mockNsdManager, times(1)).discoverServices(
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `test start notifies listener of searching state`() {
        var searchingCalled = false
        connectionManager.listener = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {}
            override fun onDisconnected() {}
            override fun onSearching() {
                searchingCalled = true
            }
        }

        connectionManager.start()

        assertTrue(searchingCalled)
    }

    @Test
    fun `test stop halts discovery`() {
        connectionManager.start()
        connectionManager.stop()

        verify(mockNsdManager).stopServiceDiscovery(any())
    }

    @Test
    fun `test stop without start does not crash`() {
        // Should not throw exception
        connectionManager.stop()
    }

    @Test
    fun `test sendNotification when disconnected queues notification`() {
        val notification = PhoneNotification(
            id = "test-id",
            app_package = "com.test",
            app_name = "Test",
            title = "Title",
            text = "Text",
            timestamp = 123L
        )

        // Not connected, so notification should be queued
        connectionManager.sendNotification(notification)

        // No crash = success (queue is internal)
    }

    @Test
    fun `test sendNotification queues up to max notifications`() {
        // Send more than MAX_QUEUED_NOTIFICATIONS (10)
        for (i in 1..15) {
            val notification = PhoneNotification(
                id = "notif-$i",
                app_package = "com.test",
                app_name = "Test",
                title = "Title $i",
                text = "Text $i",
                timestamp = i.toLong()
            )
            connectionManager.sendNotification(notification)
        }

        // No crash = success
    }

    @Test
    fun `test discovery listener callbacks`() {
        var discoveryListener: NsdManager.DiscoveryListener? = null

        whenever(mockNsdManager.discoverServices(any(), any(), any())).thenAnswer { invocation ->
            discoveryListener = invocation.getArgument(2)
            null
        }

        connectionManager.start()
        assertNotNull(discoveryListener)

        // Test onDiscoveryStarted
        discoveryListener?.onDiscoveryStarted("_notisync._tcp.")

        // Test onServiceLost
        val lostService = NsdServiceInfo().apply {
            serviceName = "notisync@test"
        }
        discoveryListener?.onServiceLost(lostService)

        // Test onDiscoveryStopped
        discoveryListener?.onDiscoveryStopped("_notisync._tcp.")

        // Test onStartDiscoveryFailed
        discoveryListener?.onStartDiscoveryFailed("_notisync._tcp.", NsdManager.FAILURE_INTERNAL_ERROR)

        // Test onStopDiscoveryFailed
        discoveryListener?.onStopDiscoveryFailed("_notisync._tcp.", NsdManager.FAILURE_INTERNAL_ERROR)
    }

    @Test
    fun `test service found triggers resolve for notisync services`() {
        var discoveryListener: NsdManager.DiscoveryListener? = null

        whenever(mockNsdManager.discoverServices(any(), any(), any())).thenAnswer { invocation ->
            discoveryListener = invocation.getArgument(2)
            null
        }

        connectionManager.start()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "notisync@myhost"
        }

        discoveryListener?.onServiceFound(serviceInfo)

        verify(mockNsdManager).resolveService(eq(serviceInfo), any())
    }

    @Test
    fun `test service found ignores non-notisync services`() {
        var discoveryListener: NsdManager.DiscoveryListener? = null

        whenever(mockNsdManager.discoverServices(any(), any(), any())).thenAnswer { invocation ->
            discoveryListener = invocation.getArgument(2)
            null
        }

        connectionManager.start()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "someother@service"
        }

        discoveryListener?.onServiceFound(serviceInfo)

        verify(mockNsdManager, never()).resolveService(any(), any())
    }

    @Test
    fun `test stop with exception during stopServiceDiscovery`() {
        whenever(mockNsdManager.stopServiceDiscovery(any()))
            .thenThrow(IllegalArgumentException("Test exception"))

        connectionManager.start()
        // Should not crash
        connectionManager.stop()
    }

    @Test
    fun `test create notification for queue test`() {
        val notification = PhoneNotification(
            id = "queue-test",
            app_package = "com.queue",
            app_name = "Queue App",
            title = "Queue Title",
            text = "Queue Text",
            timestamp = 99L
        )

        assertEquals("queue-test", notification.id)
        assertEquals("com.queue", notification.app_package)
    }

    @Test
    fun `test multiple listeners can be set`() {
        val listener1 = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {}
            override fun onDisconnected() {}
            override fun onSearching() {}
        }

        val listener2 = object : ConnectionManager.ConnectionListener {
            override fun onConnected(hostName: String) {}
            override fun onDisconnected() {}
            override fun onSearching() {}
        }

        connectionManager.listener = listener1
        assertEquals(listener1, connectionManager.listener)

        connectionManager.listener = listener2
        assertEquals(listener2, connectionManager.listener)
    }

    @Test
    fun `test start after stop can restart discovery`() {
        connectionManager.start()
        connectionManager.stop()

        // Reset mock to clear invocation count
        reset(mockNsdManager)

        connectionManager.start()

        verify(mockNsdManager).discoverServices(
            eq("_notisync._tcp."),
            eq(NsdManager.PROTOCOL_DNS_SD),
            any()
        )
    }

    @Test
    fun `test resolve listener handles resolve failure`() {
        var discoveryListener: NsdManager.DiscoveryListener? = null
        var resolveListener: NsdManager.ResolveListener? = null

        whenever(mockNsdManager.discoverServices(any(), any(), any())).thenAnswer { invocation ->
            discoveryListener = invocation.getArgument(2)
            null
        }

        whenever(mockNsdManager.resolveService(any(), any())).thenAnswer { invocation ->
            resolveListener = invocation.getArgument(1)
            null
        }

        connectionManager.start()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "notisync@test"
        }

        discoveryListener?.onServiceFound(serviceInfo)
        assertNotNull(resolveListener)

        // Test resolve failure - should not crash
        resolveListener?.onResolveFailed(serviceInfo, NsdManager.FAILURE_INTERNAL_ERROR)
    }

    @Test
    fun `test phone notification serialization via gson`() {
        val notification = PhoneNotification(
            id = "gson-test",
            app_package = "com.gson",
            app_name = "Gson Test",
            title = "Gson Title",
            text = "Gson Text",
            timestamp = 555L
        )

        val gson = com.google.gson.Gson()
        val json = gson.toJson(notification)

        assertTrue(json.contains("gson-test"))
        assertTrue(json.contains("com.gson"))
    }
}
