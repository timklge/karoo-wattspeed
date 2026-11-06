package de.timklge.karoowattspeed

import io.hammerhead.karooext.models.DataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the extension advertises while the rider searches for sensors on the Karoo. */
class WattSpeedScanTest : KarooWattSpeedTestBase() {

    @Test
    fun `the scan advertises the virtual speed sensor under the extension id`() {
        val scan = karoo.host<KarooWattSpeedExtension>().startScan()

        val device = scan.await(10_000) { it.uid == KarooWattSpeedExtension.DEVICE_UID }

        assertEquals(KarooWattSpeedExtension.TAG, device.extension)
        // The setup screen matches the paired sensor on this id, so it has to stay in step.
        assertEquals(KarooWattSpeedExtension.DEVICE_FULL_UID, device.deviceUid)
        assertEquals(karoo.app.getString(R.string.app_name), device.displayName)
        assertTrue(
            device.dataTypes.containsAll(
                listOf(
                    DataType.Source.SPEED,
                    KarooWattSpeedExtension.TRAVELED_DISTANCE_DATA_TYPE_ID,
                )
            )
        )
    }

    @Test
    fun `the scan keeps advertising while the sensor search is open`() {
        val scan = karoo.host<KarooWattSpeedExtension>().startScan()
        scan.await(10_000) { it.uid == KarooWattSpeedExtension.DEVICE_UID }

        // after = 1 skips the first advertisement, so this waits for a repeat.
        val again = scan.await(10_000, after = 1) { it.uid == KarooWattSpeedExtension.DEVICE_UID }

        assertEquals(KarooWattSpeedExtension.DEVICE_UID, again.uid)
    }

    @Test
    fun `stopping the scan stops the advertisements`() {
        val host = karoo.host<KarooWattSpeedExtension>()
        val scan = host.startScan()
        scan.await(10_000) { it.uid == KarooWattSpeedExtension.DEVICE_UID }
        val advertised = scan.items.size

        host.stopScan(scan)

        // Throws if the one-second advertisement loop survives the stop.
        scan.awaitQuiet(quietMs = 1_300, timeoutMs = 5_000)
        assertEquals(advertised, scan.items.size)
    }
}
