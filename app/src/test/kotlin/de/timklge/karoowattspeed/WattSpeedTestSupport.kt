package de.timklge.karoowattspeed

import android.app.Application
import fi.nikosavola.karooext.testing.robolectric.FakeKarooBinding
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.UserProfile
import org.junit.After
import org.junit.Rule
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
abstract class KarooWattSpeedTestBase {
    @get:Rule
    val karoo = FakeKarooRule()

    @After
    fun tearDown() {
        // Rules wrap @After, so destroy the services before Koin goes away.
        karoo.close()
        stopKoin()
    }

    protected fun rideProfile(indoor: Boolean, id: String = "profile") =
        RideProfile(id, "Profile", emptyList(), indoor, "cycling", "road")

    protected fun userProfile(weightKg: Float): UserProfile = UserProfile(
        weight = weightKg,
        preferredUnit = UserProfile.PreferredUnit(
            UserProfile.PreferredUnit.UnitType.METRIC,
            UserProfile.PreferredUnit.UnitType.METRIC,
            UserProfile.PreferredUnit.UnitType.METRIC,
            UserProfile.PreferredUnit.UnitType.METRIC,
        ),
        maxHr = 190,
        restingHr = 50,
        heartRateZones = emptyList(),
        ftp = 250,
        powerZones = emptyList(),
    )

    /** Publishes a native power reading, the input the virtual speed is derived from. */
    protected fun supplyPower(watts: Double) =
        karoo.system.setDataPoint(
            DataPoint(DataType.Type.POWER, mapOf(DataType.Field.POWER to watts))
        )
}
