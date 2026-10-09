package de.timklge.karoowattspeed

import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.RideProfile
import org.junit.Test

class KarooWattSpeedExtensionTest : KarooWattSpeedTestBase() {
    private fun profile(indoor: Boolean) =
        RideProfile("p", "Profile", emptyList(), indoor, "cycling", "road")

    @Test
    fun `indoor power produces virtual speed`() {
        karoo.system.setActiveRideProfile(profile(indoor = true))
        karoo.system.setDataPoint(DataPoint(DataType.Type.POWER, mapOf(DataType.Field.POWER to 250.0)))

        val device = karoo.host<KarooWattSpeedExtension>().connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        device.await(10_000) {
            it is OnDataPoint && it.dataPoint.dataTypeId == DataType.Source.SPEED &&
                it.dataPoint.values.getValue(DataType.Field.SPEED) > 0.0
        }
    }

    @Test
    fun `outdoor profile reports the virtual device disconnected`() {
        karoo.system.setActiveRideProfile(profile(indoor = false))

        val device = karoo.host<KarooWattSpeedExtension>().connectDevice(KarooWattSpeedExtension.DEVICE_UID)

        device.await(10_000) { it is OnConnectionStatus && it.status == ConnectionStatus.DISCONNECTED }
    }
}
