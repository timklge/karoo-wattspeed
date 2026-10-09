package de.timklge.karoowattspeed

import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.FakeKarooHost
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The virtual speed the extension derives from power, and the distance it reports alongside it. */
class WattSpeedVirtualSpeedTest : KarooWattSpeedTestBase() {

    private fun connectIndoor(
        powerWatts: Double? = null,
        uid: String = KarooWattSpeedExtension.DEVICE_UID,
        host: FakeKarooHost = karoo.host<KarooWattSpeedExtension>(),
    ): DeviceRecorder {
        karoo.system.setActiveRideProfile(rideProfile(indoor = true))
        powerWatts?.let(::supplyPower)
        return host.connectDevice(uid)
    }

    private fun DeviceRecorder.nextSpeed(after: Int = mark()): Double =
        awaitDataPoint(DataType.Source.SPEED, after = after).singleValue!!

    /**
     * Waits out the clamped first stroke of a run and returns the stroke after it, which is the
     * first one the rider's mass changes the outcome of.
     */
    private fun DeviceRecorder.strokeAfterFirst(): Double {
        awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 0.0 }
        return nextSpeed()
    }

    @Test
    fun `an indoor ride publishes speed and the matching distance every update`() {
        val device = connectIndoor(powerWatts = 250.0)
        device.awaitDataPoint(KarooWattSpeedExtension.TRAVELED_DISTANCE_DATA_TYPE_ID) {
            it.singleValue!! > 0.0
        }

        val speeds = device.dataPoints(DataType.Source.SPEED)
        val distances = device.dataPoints(KarooWattSpeedExtension.TRAVELED_DISTANCE_DATA_TYPE_ID)
        // Both points go out once per update, so truncating to the shorter list keeps the pair aligned.
        val paired = minOf(speeds.size, distances.size)
        assertTrue("expected at least two updates, got $paired", paired >= 2)

        for (i in 0 until paired) {
            assertEquals(setOf(DataType.Field.SPEED), speeds[i].values.keys)
            assertEquals(setOf(DataType.Field.DISTANCE), distances[i].values.keys)
            // Distance is speed times the 500 ms update interval.
            assertEquals(speeds[i].singleValue!! * 0.5, distances[i].singleValue!!, 1e-9)
        }
    }

    @Test
    fun `speed only rises by the acceleration limit per update`() {
        val device = connectIndoor(powerWatts = 5000.0)

        // At five kilowatts the rider is far below terminal speed, so every early update is clamped.
        val first = device.awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 0.0 }
        val second = device.nextSpeed()
        val third = device.nextSpeed()

        assertEquals(2.5, first.singleValue!!, 1e-9)
        assertEquals(5.0, second, 1e-9)
        assertEquals(7.5, third, 1e-9)
    }

    @Test
    fun `a heavier rider accelerates more slowly at the same power`() {
        karoo.system.setUserProfile(userProfile(weightKg = 50f))
        val device = connectIndoor(powerWatts = 250.0)
        val light = device.strokeAfterFirst()

        // A new weight restarts the run from a standstill, so the same stroke can be compared.
        val beforeHeavy = device.mark()
        karoo.system.setUserProfile(userProfile(weightKg = 150f))
        device.awaitDataPoint(DataType.Source.SPEED, after = beforeHeavy) { it.singleValue == 2.5 }
        val heavy = device.nextSpeed()

        assertTrue("light=$light heavy=$heavy", light > heavy + 0.4)
    }

    @Test
    fun `the virtual speed decays to zero after pedaling stops`() {
        val device = connectIndoor(powerWatts = 250.0)
        device.awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 0.0 }

        supplyPower(0.0)
        val stopped = device.mark()
        val atRest = device.awaitDataPoint(DataType.Source.SPEED, timeoutMs = 25_000, after = stopped) {
            it.singleValue == 0.0
        }

        assertEquals(0.0, atRest.singleValue!!, 0.0)
        assertTrue(device.dataPoints(DataType.Source.SPEED).all { it.singleValue!! >= 0.0 })
    }

    @Test
    fun `the virtual speed pauses and resumes when the power sensor drops out`() {
        val device = connectIndoor(powerWatts = 250.0)
        val rolling = device.awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 3.0 }

        val beforeDrop = device.mark()
        karoo.system.setStreamState(DataType.Type.POWER, StreamState.NotAvailable)
        val slowing = device.awaitDataPoint(DataType.Source.SPEED, after = beforeDrop) {
            it.singleValue!! < rolling.singleValue!!
        }

        val afterDrop = device.mark()
        supplyPower(300.0)
        val recovered = device.awaitDataPoint(DataType.Source.SPEED, after = afterDrop) {
            it.singleValue!! > rolling.singleValue!!
        }

        assertTrue(recovered.singleValue!! > slowing.singleValue!!)
    }

    @Test
    fun `a missing power sensor still publishes a standstill`() {
        karoo.system.initialStreamState = StreamState.NotAvailable
        val device = connectIndoor()

        val atRest = device.awaitDataPoint(DataType.Source.SPEED)

        assertEquals(0.0, atRest.singleValue!!, 0.0)
        // The field keeps updating with zero, so a ride page never shows a stale value.
        device.awaitDataPoint(DataType.Source.SPEED, after = device.mark())
    }

    @Test
    fun `the published points name the connected device as their source`() {
        val device = connectIndoor(powerWatts = 250.0, uid = "some-other-uid")

        val point = device.awaitDataPoint(DataType.Source.SPEED) { it.singleValue!! > 0.0 }

        assertEquals("some-other-uid", point.sourceId)
        assertNotEquals(KarooWattSpeedExtension.DEVICE_UID, point.sourceId)
    }
}
