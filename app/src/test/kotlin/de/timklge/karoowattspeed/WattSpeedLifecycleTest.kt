package de.timklge.karoowattspeed

import fi.nikosavola.karooext.testing.DeviceRecorder
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.OnManufacturerInfo
import io.hammerhead.karooext.models.OnStreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Connect, profile switching and teardown: when the virtual device is on, and what it leaves behind. */
class WattSpeedLifecycleTest : KarooWattSpeedTestBase() {

    private fun DeviceRecorder.awaitFirstSpeed() =
        awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 0.0 }

    @Test
    fun `an indoor ride connects and identifies the extension as its manufacturer`() {
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))

        val device = karoo.host<KarooWattSpeedExtension>()
            .connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        device.awaitConnected()
        val info = device.awaitOf<OnManufacturerInfo>()
        assertEquals(KarooWattSpeedExtension.TAG, info.info.manufacturer)
        // The ride app expects the status handshake before anything else on the session.
        assertTrue(device.items.first() is OnConnectionStatus)
    }

    @Test
    fun `the extension waits for the ride profile before reporting a status`() {
        val device = karoo.host<KarooWattSpeedExtension>()
            .connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        // Registered but silent: the ride app has not said whether this is an indoor ride.
        karoo.awaitConsumer(ActiveRideProfile.Params)
        device.awaitQuiet(quietMs = 1_200, timeoutMs = 4_000)

        assertTrue(device.items.isEmpty())
    }

    @Test
    fun `an outdoor ride publishes no speed or distance`() {
        karoo.system.setActiveRideProfile(rideProfile(indoor = false))

        val device = karoo.host<KarooWattSpeedExtension>()
            .connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        device.awaitStatus(ConnectionStatus.DISCONNECTED)
        // Throws if points keep arriving for the whole timeout.
        device.awaitQuiet(quietMs = 1_200, timeoutMs = 4_000)

        assertTrue(device.items.filterIsInstance<OnDataPoint>().isEmpty())
    }

    @Test
    fun `switching to an outdoor profile disconnects and drops the power consumer`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val device = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        // A positive speed can only come from a power reading, so the power consumer is up.
        device.awaitFirstSpeed()
        val streaming = karoo.system.consumerCount

        karoo.system.setActiveRideProfile(rideProfile(indoor = false, id = "outdoor"))

        device.awaitStatus(ConnectionStatus.DISCONNECTED)
        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == streaming - 1 } }
        assertTrue(
            karoo.system.consumerParams.none {
                it == OnStreamState.StartStreaming(DataType.Type.POWER)
            }
        )

        val before = device.mark()
        device.awaitQuiet(quietMs = 1_200, timeoutMs = 4_000)
        assertEquals(before, device.items.size)
    }

    @Test
    fun `an outdoor ride connects when the profile turns indoor`() {
        karoo.system.setActiveRideProfile(rideProfile(indoor = false))
        supplyPower(250.0)
        val device = karoo.host<KarooWattSpeedExtension>()
            .connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        device.awaitStatus(ConnectionStatus.DISCONNECTED)

        karoo.system.setActiveRideProfile(rideProfile(indoor = true, id = "indoor"))

        device.awaitConnected()
        device.awaitFirstSpeed()
    }

    @Test
    fun `disconnecting the device releases every consumer the extension registered`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        val idle = karoo.system.consumerCount
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val device = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        device.awaitFirstSpeed()

        host.disconnectDevice(device)

        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == idle } }
        val before = device.mark()
        device.awaitQuiet(quietMs = 1_200, timeoutMs = 4_000)
        assertEquals(before, device.items.size)
    }

    @Test
    fun `a second connect on the same extension works`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        val idle = karoo.system.consumerCount
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val first = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        first.awaitFirstSpeed()
        host.disconnectDevice(first)
        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == idle } }

        val second = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        second.awaitConnected()
        second.awaitFirstSpeed()
    }

    @Test
    fun `flipping the ride profile does not leak consumers`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val device = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        device.awaitFirstSpeed()
        val indoor = karoo.system.consumerCount

        repeat(3) { round ->
            karoo.system.setActiveRideProfile(rideProfile(indoor = false, id = "outdoor-$round"))
            assertTrue(karoo.awaitValue { true.takeIf { karoo.system.consumerCount == indoor - 1 } })
            karoo.system.setActiveRideProfile(rideProfile(indoor = true, id = "indoor-$round"))
            assertTrue(karoo.awaitValue { true.takeIf { karoo.system.consumerCount == indoor } })
        }

        device.awaitDataPoint(DataType.Source.SPEED, after = device.mark()) {
            it.singleValue!! > 0.0
        }
    }

    @Test
    fun `the extension resumes publishing after being killed and started again`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        val idle = karoo.system.consumerCount
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val first = host.connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        first.awaitFirstSpeed()
        // Let the killed session finish unregistering first: the rule reads the fake's consumer
        // table while restarting, and an extension tearing down on its own thread races that read.
        host.disconnectDevice(first)
        karoo.awaitValue { true.takeIf { karoo.system.consumerCount == idle } }

        val restarted = karoo.restart<KarooWattSpeedExtension>()
        val device = restarted.connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        device.awaitConnected()
        device.awaitFirstSpeed()
    }

    @Test
    fun `changing the user profile restarts the ride from a standstill`() {
        karoo.system.setUserProfile(userProfile(weightKg = 75f))
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        supplyPower(250.0)
        val device = karoo.host<KarooWattSpeedExtension>()
            .connectDevice(KarooWattSpeedExtension.DEVICE_UID)
        val rolling = device.awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 3.0 }
        val before = device.mark()

        karoo.system.setUserProfile(userProfile(weightKg = 80f))

        // Down to a standstill first, then climbing again from there.
        val restarted = device.awaitDataPoint(DataType.Source.SPEED, after = before) {
            it.singleValue!! < rolling.singleValue!!
        }
        val rollingAgain = device.awaitDataPoint(DataType.Source.SPEED, after = device.mark()) {
            it.singleValue!! > rolling.singleValue!!
        }

        assertTrue(restarted.singleValue!! < rollingAgain.singleValue!!)
    }
}
