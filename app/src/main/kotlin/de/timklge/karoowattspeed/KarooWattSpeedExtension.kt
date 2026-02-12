package de.timklge.karoowattspeed

import android.util.Log
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.ManufacturerInfo
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.OnManufacturerInfo
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Duration
import java.time.Instant

class KarooWattSpeedExtension : KarooExtension(TAG, "1.0") {

    companion object {
        const val TAG = "karoo-wattspeed"
        const val DEVICE_UID = "wattspeed-virtual-device-2"
        const val DEVICE_FULL_UID = "$TAG::$DEVICE_UID"
        const val UPDATE_INTERVAL = 500L
        const val TRAVELED_DISTANCE_DATA_TYPE_ID = "TYPE_SPD_DISTANCE_DIFF_ID"
        const val MAX_ACCELERATION = 5.0 // Maximum acceleration in m/s² (realistic for a bicycle)
    }

    private val karooSystem: KarooSystemServiceProvider by inject()

    override fun startScan(emitter: Emitter<Device>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                emitter.onNext(Device(TAG,
                    DEVICE_UID,
                    listOf(DataType.Source.SPEED, TRAVELED_DISTANCE_DATA_TYPE_ID),
                    applicationContext.getString(R.string.app_name))
                )

                delay(1_000)
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Log.d(TAG, "Connect to $uid")

        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.karooSystemService.streamUserProfile().distinctUntilChanged().collectLatest { userProfile ->
                karooSystem.karooSystemService.streamActiveRideProfile().map { rideProfile -> rideProfile.profile.indoor }.distinctUntilChanged().collectLatest { isIndoor ->
                    Log.d(TAG, "User profile changed: $userProfile, indoor: $isIndoor")

                    if (isIndoor) {
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
                        emitter.onNext(OnManufacturerInfo(ManufacturerInfo(TAG)))

                        coroutineScope {
                            var lastPowerData: Double? = null
                            var lastCalculatedSpeed = 0.0
                            var lastPowerSuppliedAt: Instant? = null

                            launch {
                                karooSystem.karooSystemService.streamDataFlow(DataType.Type.POWER).collect { powerData ->
                                    lastPowerData = (powerData as? StreamState.Streaming)?.dataPoint?.singleValue
                                }
                            }

                            while (isActive) {
                                val lastPower = lastPowerData ?: 0.0
                                if (lastPower > 0) lastPowerSuppliedAt = Instant.now()
                                val calculatedSpeedInMs = calculateSpeedFromPower(lastCalculatedSpeed, lastPower, userProfile.weight.toDouble(), lastPowerSuppliedAt)
                                lastCalculatedSpeed = calculatedSpeedInMs

                                Log.i(TAG, "Emitting speed data point: $calculatedSpeedInMs m/s based on power: $lastPower W")

                                emitter.onNext(OnDataPoint(DataPoint(DataType.Source.SPEED,
                                    mapOf(DataType.Field.SPEED to calculatedSpeedInMs),
                                    uid
                                )))

                                val traveledDistance = (calculatedSpeedInMs * (UPDATE_INTERVAL / 1000.0)) // distance = speed * time

                                emitter.onNext(OnDataPoint(DataPoint(TRAVELED_DISTANCE_DATA_TYPE_ID,
                                    mapOf(DataType.Field.DISTANCE to traveledDistance),
                                    uid
                                )))

                                delay(UPDATE_INTERVAL)
                            }
                        }
                    } else {
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.DISCONNECTED))
                    }
                }
            }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Calculate speed in m/s based on current power in watts, user weight in kg, and the last known speed [currentSpeed].
     */
    private fun calculateSpeedFromPower(currentSpeed: Double, power: Double, userWeight: Double, lastPowerSuppliedAt: Instant?): Double {
        // Constants for road bike aero position and tarmac rolling resistance
        val rho = 1.225 // Air density (kg/m^3) at sea level
        val g = 9.81    // Gravity (m/s^2)
        val crr = 0.005 // Rolling resistance coefficient for tarmac
        val cda = 0.32  // Coefficient of drag * frontal area for aero road position
        val mass = userWeight + 9.0 // Total mass of rider and bike in kg
        val deltaTime = UPDATE_INTERVAL / 1000.0 // Time step in seconds

        // Calculate resistance forces at current speed
        val dragForce = 0.5 * rho * cda * currentSpeed * currentSpeed
        val rollingForce = crr * mass * g
        val totalResistanceForce = dragForce + rollingForce

        // Calculate driving force from power
        // Power = Force × Velocity, so Force = Power / Velocity
        // To avoid division by zero at v=0, use a small minimum speed
        val effectiveSpeed = maxOf(currentSpeed, 0.01)
        val drivingForce = power / effectiveSpeed

        // Net force = driving force - resistance forces
        val netForce = drivingForce - totalResistanceForce

        // F = m × a, so acceleration a = F / m
        val acceleration = netForce / mass

        // Update velocity: v_new = v_old + a × Δt
        val newSpeed = currentSpeed + (acceleration * deltaTime)

        // Limit acceleration to maximum value
        val maxAcceleration = MAX_ACCELERATION * deltaTime
        val limitedSpeed = if (acceleration > maxAcceleration) {
            currentSpeed + maxAcceleration
        } else if (acceleration < -maxAcceleration) {
            currentSpeed - maxAcceleration
        } else {
            newSpeed
        }

        if (lastPowerSuppliedAt != null && lastPowerSuppliedAt < Instant.now().minus(Duration.ofSeconds(3))) {
            // If no power has been supplied for more than 5 seconds, assume the rider has stopped pedaling and apply a stronger deceleration
            val coastingDeceleration = 2.0 * deltaTime // Coasting deceleration in m/s²
            return maxOf(0.0, limitedSpeed - coastingDeceleration)
        }

        // Speed cannot be negative
        return maxOf(0.0, limitedSpeed)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
