package de.timklge.karoowattspeed

import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.OnConnectionStatus

enum class MoxyMonitorConnectionState {
    SEARCHING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

fun OnConnectionStatus.mapToMoxyMonitorConnectionState(): MoxyMonitorConnectionState {
    return when (this.status) {
        ConnectionStatus.SEARCHING -> MoxyMonitorConnectionState.SEARCHING
        ConnectionStatus.CONNECTED -> MoxyMonitorConnectionState.CONNECTED
        ConnectionStatus.DISCONNECTED, ConnectionStatus.DISABLED -> MoxyMonitorConnectionState.DISCONNECTED
    }
}