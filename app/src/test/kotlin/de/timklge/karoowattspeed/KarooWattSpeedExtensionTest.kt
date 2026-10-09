package de.timklge.karoowattspeed

import android.app.Application
import fi.nikosavola.karooext.testing.robolectric.FakeKarooBinding
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.RideProfile
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The app connects to Karoo while Koin starts, before any rule runs, so install the fake first.
class TestApplication : Application() {
    override fun onCreate() {
        FakeKarooBinding.installEarly(this)
        super.onCreate()
        startKoin {
            androidContext(this@TestApplication)
            modules(appModule)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApplication::class)
class KarooWattSpeedExtensionTest {
    @get:Rule val karoo = FakeKarooRule()

    @After
    fun tearDown() {
        // Rules wrap @After, so destroy the services before Koin goes away.
        karoo.close()
        stopKoin()
    }

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
