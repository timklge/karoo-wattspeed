package de.timklge.karoowattspeed.screens

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karoowattspeed.KarooSystemServiceProvider
import de.timklge.karoowattspeed.KarooWattSpeedExtension
import de.timklge.karoowattspeed.R
import de.timklge.karoowattspeed.streamActiveRideProfile
import de.timklge.karoowattspeed.streamDataFlow
import de.timklge.karoowattspeed.streamSavedDevices
import de.timklge.karoowattspeed.streamUserProfile
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

val speedDataTypes = setOf(
    "TYPE_POWER_SPEED_ID", "TYPE_CSC_SPEED_ID", "TYPE_LOCATION_SPEED_ID",
    DataType.Type.SPEED, DataType.Source.SPEED
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val karooSystem = koinInject<KarooSystemServiceProvider>()
    var karooSystemConnectionPeriodElapsed by remember { mutableStateOf(false) }
    val knownDevices by karooSystem.karooSystemService.streamSavedDevices().collectAsStateWithLifecycle(null)
    val karooConnected by karooSystem.connectionStateFlow.collectAsStateWithLifecycle(false)
    val activeRideProfile by karooSystem.karooSystemService.streamActiveRideProfile().collectAsStateWithLifecycle(null)

    LaunchedEffect(Unit) {
        delay(1_000L)
        karooSystemConnectionPeriodElapsed = true
    }

    @Composable
    fun OpenSensorSearchActivityButton(){
        Button(
            onClick = {
                ctx.startActivity(
                    Intent().setClassName(
                        "io.hammerhead.sensorsapp",
                        "io.hammerhead.sensorsapp.sensorSearch.SensorSearchActivity"
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Add Sensor", fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun OpenSensorSettingsActivityButton(){
        Button(
            onClick = {
                ctx.startActivity(
                    Intent().setClassName(
                        "io.hammerhead.sensorsapp",
                        "io.hammerhead.sensorsapp.sensorList.SensorListActivity"
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Open Sensor List", fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    fun StatusCard(
        backgroundColor: Color,
        content: @Composable () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ){
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
            )

            val appState by remember {
                derivedStateOf {
                    val isIndoor = activeRideProfile?.profile?.indoor
                    Log.i(KarooWattSpeedExtension.TAG, "Known devices: $knownDevices, karooConnected: $karooConnected, karooSystemConnectionPeriodElapsed: $karooSystemConnectionPeriodElapsed, isIndoor: $isIndoor")

                    val isEnabled = knownDevices?.devices?.any { device -> device.id == KarooWattSpeedExtension.DEVICE_FULL_UID && device.enabled } == true && isIndoor == true
                    val indexInSensorList = knownDevices?.devices?.indexOfFirst { device -> device.id == KarooWattSpeedExtension.DEVICE_FULL_UID } ?: -1
                    val isPaired = indexInSensorList >= 0
                    val indexOfFirstSpeedSensorInList = knownDevices?.devices?.indexOfFirst { device ->
                        device.supportedDataTypes.intersect(speedDataTypes).isNotEmpty()
                    } ?: -1

                    when {
                        !karooConnected && karooSystemConnectionPeriodElapsed -> AppState.KAROO_NOT_CONNECTED
                        knownDevices == null -> AppState.LOADING
                        !isPaired -> AppState.NOT_PAIRED
                        isPaired && indexOfFirstSpeedSensorInList < indexInSensorList -> AppState.NOT_PRIMARY_SOURCE
                        isPaired && !isEnabled -> AppState.DISABLED
                        isPaired && isEnabled -> AppState.CONNECTED
                        else -> AppState.LOADING
                    }
                }
            }

            when (appState) {
                AppState.KAROO_NOT_CONNECTED -> StatusCard(
                    backgroundColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Failed to connect to Karoo system. Is your karoo updated?",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                AppState.DISABLED -> StatusCard(
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Virtual Sensor is disabled.",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Text(
                            "The sensor is automatically disabled when an outdoor ride profile is active.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        val profile by karooSystem.karooSystemService.streamActiveRideProfile().collectAsStateWithLifecycle(null)

                        if (profile != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    "Current profile: ${profile!!.profile.name}",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        OpenSensorSettingsActivityButton()
                    }
                }

                AppState.NOT_PAIRED -> StatusCard(
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "WattSpeed virtual sensor is not paired.",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Text(
                            "Please search for extension devices in the \"Sensors\" menu on your Karoo and pair it.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        OpenSensorSearchActivityButton()
                    }
                }

                AppState.NOT_PRIMARY_SOURCE -> StatusCard(
                    backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Not the primary speed source",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Text(
                            "Please make sure it is at the top of the sensor list in the \"Sensors\" menu on your Karoo.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        OpenSensorSettingsActivityButton()
                    }
                }

                AppState.CONNECTED -> StatusCard(
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "WattSpeed virtual sensor is connected and active.",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        val profile by karooSystem.karooSystemService.streamUserProfile().collectAsStateWithLifecycle(null)
                        var power by remember { mutableStateOf<Double?>(null) }
                        var speed by remember { mutableStateOf<Double?>(null) }

                        LaunchedEffect(Unit) {
                            karooSystem.karooSystemService.streamDataFlow(DataType.Type.POWER).collect { powerValue ->
                                power = (powerValue as? StreamState.Streaming)?.dataPoint?.singleValue
                            }
                        }

                        LaunchedEffect(Unit) {
                            karooSystem.karooSystemService.streamDataFlow(DataType.Type.SPEED).collect { speedValue ->
                                speed = (speedValue as? StreamState.Streaming)?.dataPoint?.singleValue
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            val speedInUserUnit = when (profile?.preferredUnit?.distance) {
                                UserProfile.PreferredUnit.UnitType.METRIC -> speed?.times(3.6)?.let { String.format("%.1f km/h", it) }
                                UserProfile.PreferredUnit.UnitType.IMPERIAL -> speed?.times(2.23694)?.let { String.format("%.1f mph", it) }
                                else -> null
                            }

                            val powerLabel = if (power != null) {
                                "$power W"
                            } else {
                                "N/A"
                            }

                            Text(
                                "Power: ${powerLabel}\r\nSpeed: ${speedInUserUnit ?: "N/A"}",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        OpenSensorSettingsActivityButton()
                    }
                }

                AppState.LOADING -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        "Loading...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    onFinish()
                }
        )
    }
}
