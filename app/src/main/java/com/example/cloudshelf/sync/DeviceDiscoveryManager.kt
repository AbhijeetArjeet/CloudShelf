package com.example.cloudshelf.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

data class DiscoveredReceiver(
    val serviceName: String,
    val host: InetAddress,
    val port: Int
)

class DeviceDiscoveryManager(private val context: Context) {

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _discoveredReceivers = MutableStateFlow<List<DiscoveredReceiver>>(emptyList())
    val discoveredReceivers: StateFlow<List<DiscoveredReceiver>> = _discoveredReceivers.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        const val SERVICE_TYPE = "_cloudshelf._tcp."
        const val DEFAULT_PORT = 8844
        private const val TAG = "DeviceDiscovery"
    }

    /**
     * Receiver (Pixel): Advertises CloudShelf service on local Wi-Fi via mDNS / NSD.
     */
    fun startAdvertising(deviceName: String, port: Int = DEFAULT_PORT) {
        stopAdvertising()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "CloudShelf-$deviceName"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) {
                Log.i(TAG, "NSD Service registered: ${service.serviceName} on port $port")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD service", e)
        }
    }

    fun stopAdvertising() {
        registrationListener?.let {
            try {
                nsdManager?.unregisterService(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering NSD service", e)
            }
            registrationListener = null
        }
    }

    /**
     * Source Device (Companion): Discovers active Pixel receivers on local Wi-Fi.
     */
    fun startDiscovery() {
        stopDiscovery()
        _discoveredReceivers.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "NSD Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_cloudshelf._tcp")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredReceivers.value = _discoveredReceivers.value.filter { it.serviceName != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering NSD services", e)
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host ?: return
                val port = serviceInfo.port
                val receiver = DiscoveredReceiver(serviceInfo.serviceName, host, port)
                val current = _discoveredReceivers.value.toMutableList()
                if (current.none { it.host == host && it.port == port }) {
                    current.add(receiver)
                    _discoveredReceivers.value = current
                }
            }
        }
        try {
            nsdManager?.resolveService(service, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error initiating NSD resolve", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping NSD discovery", e)
            }
            discoveryListener = null
        }
    }
}
