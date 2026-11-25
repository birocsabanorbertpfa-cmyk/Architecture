package hu.csabi.architecture.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.csabi.architecture.core.connectivity.NetworkMonitor
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Lesson 13 — `callbackFlow`, the bridge from a listener API to a Flow.
 *
 * This is the canonical shape, and every line of it is load-bearing:
 *
 *  1. `callbackFlow` (not `flow`) because the callback fires on **another thread**. A plain
 *     `flow {}` builder may only emit from the collecting coroutine, so `emit` from a
 *     callback throws `IllegalStateException`. `trySend` on the channel is thread-safe.
 *  2. `trySend` rather than `send`: the callback is not a suspending context, so there is
 *     nothing to suspend. With `conflate` below, a dropped send only ever loses a duplicate.
 *  3. `awaitClose` is **mandatory** — `callbackFlow` throws if the block returns without it.
 *     That is a deliberate design: it forces you to write the unregister, which is exactly
 *     the line people forget and then leak a listener per screen rotation.
 *
 * The flow is cold, so each collector registers its own callback; `stateIn`/`shareIn` in a
 * ViewModel is what makes one registration serve many observers.
 */
class ConnectivityNetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            // No connectivity service at all: report offline once and finish, rather than
            // leaving the collector waiting forever for an event that cannot arrive.
            trySend(false)
            close()
            return@callbackFlow
        }

        /**
         * The callback tracks a *set* of networks on purpose. A device can have wifi and
         * cellular at once, and `onLost(wifi)` while cellular is still up does not mean
         * offline — a single boolean flag gets this wrong every time the user walks out of
         * wifi range.
         */
        val networks = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                networks -= network
                trySend(networks.isNotEmpty())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // VALIDATED is the difference between "there is a network" and "it can reach the
            // internet". Without it, a captive portal reads as online.
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        manager.registerNetworkCallback(request, callback)

        // The callback only reports *changes*, so the current state has to be seeded or the
        // first emission waits for the user to walk into a tunnel.
        trySend(manager.isCurrentlyOnline())

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
