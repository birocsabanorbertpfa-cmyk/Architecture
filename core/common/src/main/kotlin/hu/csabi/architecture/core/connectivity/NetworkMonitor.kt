package hu.csabi.architecture.core.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Lesson 13 — connectivity as a stream, behind a port.
 *
 * The interface lives in a plain JVM module, so it mentions no `ConnectivityManager` and no
 * `Context`: callers see a `Flow<Boolean>` and nothing else. The Android implementation sits
 * in `:data`, and a test substitutes a `MutableStateFlow` — which is the only reason
 * "retries when the connection returns" is testable at all.
 */
interface NetworkMonitor {

    /**
     * Whether the device currently has a validated internet connection.
     *
     * Validated, not merely connected: a captive portal (hotel wifi asking for a login) is
     * "connected" and cannot reach anything. Treating that as online is why apps spin
     * forever on hotel networks.
     */
    val isOnline: Flow<Boolean>
}
