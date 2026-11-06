package de.timklge.karoowattspeed

import io.hammerhead.karooext.models.RequestBluetooth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

/** The Koin singleton that binds the extension to the Karoo system app. */
class KarooSystemServiceProviderTest : KarooWattSpeedTestBase() {

    private val provider: KarooSystemServiceProvider
        get() = GlobalContext.get().get()

    @Test
    fun `connecting to Karoo claims the Bluetooth resource`() {
        val request = karoo.awaitEffect<RequestBluetooth> {
            it.resourceId == KarooWattSpeedExtension.TAG
        }

        assertEquals(KarooWattSpeedExtension.TAG, request.resourceId)
        assertTrue(KarooWattSpeedExtension.TAG in karoo.system.bluetoothClaims)
    }

    @Test
    fun `the connection state flow reports the Karoo system as connected`() {
        // Bounded, because the connect callback needs the main looper while this thread waits.
        fun replayed(): Boolean? =
            runBlocking { withTimeoutOrNull(200) { provider.connectionStateFlow.first() } }

        assertTrue(karoo.awaitValue { replayed()?.takeIf { it } })
    }
}
