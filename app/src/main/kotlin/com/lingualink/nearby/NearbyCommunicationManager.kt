package com.lingualink.nearby

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

sealed class NearbyConnectionState {
    object Disconnected : NearbyConnectionState()
    data class Advertising(val deviceName: String) : NearbyConnectionState()
    data class Discovering(val deviceName: String) : NearbyConnectionState()
    data class Connecting(val endpointName: String) : NearbyConnectionState()
    data class Connected(val endpointId: String, val endpointName: String) : NearbyConnectionState()
    data class Error(val message: String) : NearbyConnectionState()
}

/**
 * Real Google Nearby Connections API implementation using Strategy.P2P_CLUSTER.
 * Allows two phones (Tourist and Local Service Provider) to discover, pair,
 * and exchange translated messages completely offline over Bluetooth & Wi-Fi Direct.
 */
class NearbyCommunicationManager(
    private val context: Context
) {
    companion object {
        const val TAG = "NEARBY"
        const val SERVICE_ID = "com.lingualink.nearby"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val gson = Gson()

    private val _connectionState = MutableStateFlow<NearbyConnectionState>(NearbyConnectionState.Disconnected)
    val connectionState: StateFlow<NearbyConnectionState> = _connectionState.asStateFlow()

    private val _currentRole = MutableStateFlow(DeviceRole.TOURIST)
    val currentRole: StateFlow<DeviceRole> = _currentRole.asStateFlow()

    private var connectedEndpointId: String? = null
    private var connectedEndpointName: String? = null
    private var localDeviceName: String = "LinguaLink_${Build.MODEL}"

    private var messageListener: ((NearbyMessage) -> Unit)? = null

    fun setLocalDeviceName(name: String) {
        localDeviceName = name
    }

    fun setRole(role: DeviceRole) {
        _currentRole.value = role
    }

    fun setMessageListener(listener: (NearbyMessage) -> Unit) {
        this.messageListener = listener
    }

    /**
     * Payload callback for incoming Nearby messages from peer phone.
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                try {
                    val json = String(bytes, StandardCharsets.UTF_8)
                    val message = gson.fromJson(json, NearbyMessage::class.java)
                    Log.i(TAG, "Nearby payload received from $endpointId: '$json'")
                    messageListener?.invoke(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing Nearby message", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handled for byte payloads
        }
    }

    /**
     * Connection lifecycle callback.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.i(TAG, "Connection initiated from ${connectionInfo.endpointName} ($endpointId)")
            _connectionState.value = NearbyConnectionState.Connecting(connectionInfo.endpointName)
            // Auto-accept connection on both sides
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.i(TAG, "Accepted connection with $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed accepting connection with $endpointId", e)
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i(TAG, "Nearby Connection ESTABLISHED with $endpointId")
                    connectedEndpointId = endpointId
                    _connectionState.value = NearbyConnectionState.Connected(
                        endpointId,
                        connectedEndpointName ?: "Peer Device"
                    )
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by $endpointId")
                    _connectionState.value = NearbyConnectionState.Error("Connection rejected")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with $endpointId")
                    _connectionState.value = NearbyConnectionState.Error("Connection error")
                }
                else -> {
                    Log.w(TAG, "Connection result: ${resolution.status.statusMessage}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Nearby disconnected from $endpointId")
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                connectedEndpointName = null
                _connectionState.value = NearbyConnectionState.Disconnected
            }
        }
    }

    /**
     * Start Advertising this phone as a Tourist or Local Service Provider.
     */
    fun startAdvertising() {
        disconnect()
        val nameWithRole = "${localDeviceName} (${_currentRole.value.label})"
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(nameWithRole, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                Log.i(TAG, "Started Nearby Advertising as '$nameWithRole'")
                _connectionState.value = NearbyConnectionState.Advertising(nameWithRole)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed starting Nearby Advertising", e)
                _connectionState.value = NearbyConnectionState.Error("Advertising failed: ${e.message}")
            }
    }

    /**
     * Start Discovering peer phones.
     */
    fun startDiscovery() {
        disconnect()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.i(TAG, "Nearby Endpoint FOUND: ${info.endpointName} ($endpointId). Requesting connection...")
                    connectedEndpointName = info.endpointName
                    connectionsClient.requestConnection(
                        localDeviceName,
                        endpointId,
                        connectionLifecycleCallback
                    ).addOnSuccessListener {
                        Log.i(TAG, "Connection requested to $endpointId")
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed requesting connection to $endpointId", e)
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.i(TAG, "Endpoint lost: $endpointId")
                }
            },
            options
        ).addOnSuccessListener {
            Log.i(TAG, "Started Nearby Discovery...")
            _connectionState.value = NearbyConnectionState.Discovering(localDeviceName)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed starting Nearby Discovery", e)
            _connectionState.value = NearbyConnectionState.Error("Discovery failed: ${e.message}")
        }
    }

    /**
     * Sends a translated message payload to the connected peer phone.
     */
    fun sendMessage(message: NearbyMessage): Boolean {
        val endpointId = connectedEndpointId
        if (endpointId == null) {
            Log.w(TAG, "Cannot send Nearby message: No device connected")
            return false
        }

        try {
            val json = gson.toJson(message)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val payload = Payload.fromBytes(bytes)

            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.i(TAG, "Nearby message sent to $endpointId: '$json'")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send Nearby payload", e)
                }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding Nearby message", e)
            return false
        }
    }

    fun disconnect() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            connectedEndpointId = null
            connectedEndpointName = null
            _connectionState.value = NearbyConnectionState.Disconnected
            Log.i(TAG, "Nearby disconnected and stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error during disconnect: ${e.message}")
        }
    }
}
