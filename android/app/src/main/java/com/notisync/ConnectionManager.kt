package com.notisync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

class ConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionManager"
        private const val SERVICE_TYPE = "_notisync._tcp."
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_QUEUED_NOTIFICATIONS = 10
    }

    private val gson = Gson()
    private var nsdManager: NsdManager? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isSearching = AtomicBoolean(false)
    private val notificationQueue = ConcurrentLinkedQueue<PhoneNotification>()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    interface ConnectionListener {
        fun onConnected(hostName: String)
        fun onDisconnected()
        fun onSearching()
    }

    var listener: ConnectionListener? = null

    private val okHttpClient: OkHttpClient by lazy {
        // Trust all certificates (for self-signed cert)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    fun start() {
        if (isSearching.get()) return

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        startDiscovery()
    }

    fun stop() {
        stopDiscovery()
        webSocket?.close(1000, "App stopped")
        webSocket = null
        isConnected.set(false)
    }

    fun sendNotification(notification: PhoneNotification) {
        if (isConnected.get()) {
            val json = gson.toJson(notification)
            webSocket?.send(json)
            Log.d(TAG, "Sent notification: ${notification.app_name}")
        } else {
            // Queue notification for later
            notificationQueue.offer(notification)
            while (notificationQueue.size > MAX_QUEUED_NOTIFICATIONS) {
                notificationQueue.poll()
            }
            Log.d(TAG, "Queued notification: ${notification.app_name}")
        }
    }

    private fun startDiscovery() {
        isSearching.set(true)
        listener?.onSearching()
        updateSearchingState(true)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceName.startsWith("notisync")) {
                    nsdManager?.resolveService(serviceInfo, createResolveListener())
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                isSearching.set(false)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                isSearching.set(false)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
        }
        discoveryListener = null
        isSearching.set(false)
        updateSearchingState(false)
    }

    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // Get addresses - prefer IPv4 over IPv6
                val addresses: List<InetAddress> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses
                } else {
                    listOfNotNull(serviceInfo.host)
                }

                // Prefer IPv4, fall back to IPv6
                val address = addresses.filterIsInstance<Inet4Address>().firstOrNull()
                    ?: addresses.filterIsInstance<Inet6Address>().firstOrNull()
                    ?: addresses.firstOrNull()

                if (address == null) {
                    Log.e(TAG, "No valid address found for service")
                    return
                }

                Log.d(TAG, "Service resolved: ${address.hostAddress}:${serviceInfo.port}")
                connectToServer(address, serviceInfo.port)
            }
        }
    }

    private fun connectToServer(address: InetAddress, port: Int) {
        if (isConnected.get()) return

        // Format the host properly for URL - IPv6 needs brackets
        val host = when (address) {
            is Inet6Address -> {
                // Get address without zone ID and wrap in brackets
                val addrStr = address.hostAddress?.split("%")?.firstOrNull() ?: return
                "[$addrStr]"
            }
            else -> address.hostAddress ?: return
        }

        val url = "wss://$host:$port"
        Log.d(TAG, "Connecting to $url")

        val request = Request.Builder()
            .url(url)
            .build()

        val displayHost = address.hostAddress ?: host
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected.set(true)
                stopDiscovery()
                listener?.onConnected(displayHost)

                // Send queued notifications
                while (notificationQueue.isNotEmpty()) {
                    notificationQueue.poll()?.let {
                        sendNotification(it)
                    }
                }

                updateConnectionState(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleDisconnection()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                handleDisconnection()
            }
        })
    }

    private fun handleDisconnection() {
        isConnected.set(false)
        webSocket = null
        listener?.onDisconnected()
        updateConnectionState(false)

        // Retry connection after delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isConnected.get() && !isSearching.get()) {
                startDiscovery()
            }
        }, RECONNECT_DELAY_MS)
    }

    private fun updateConnectionState(connected: Boolean) {
        context.getSharedPreferences("notisync", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", connected)
            .putBoolean("searching", !connected && isSearching.get())
            .apply()
        broadcastStateChange()
    }

    private fun updateSearchingState(searching: Boolean) {
        context.getSharedPreferences("notisync", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("searching", searching)
            .apply()
        broadcastStateChange()
    }

    private fun broadcastStateChange() {
        val intent = android.content.Intent("com.notisync.STATE_CHANGED")
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(context)
            .sendBroadcast(intent)
    }
}
