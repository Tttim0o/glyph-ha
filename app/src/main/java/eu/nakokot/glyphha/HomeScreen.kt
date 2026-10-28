package eu.nakokot.glyphha

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var gesturesOn by remember { mutableStateOf(Prefs.gesturesEnabled(context)) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Manual control", style = MaterialTheme.typography.titleMedium)
        Glyph.groups.keys.forEach { g ->
            var v by remember { mutableFloatStateOf(0f) }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("$g — ${v.toInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = v, onValueChange = { v = it }, valueRange = 0f..100f,
                        onValueChangeFinished = { Glyph.set(g, v.toInt()) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Glyph.off() }) { Text("Off") }
            FilledTonalButton(onClick = { Glyph.pulse("white", 100, 500) }) { Text("Pulse") }
            FilledTonalButton(onClick = { Glyph.toggleTorch() }) {
                Icon(Icons.Filled.FlashOn, null); Spacer(Modifier.width(6.dp)); Text("Torch")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Gestures", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Shake, tap & face-down gestures")
                        Text(
                            "Shake twice quickly: toggle torch. Tap the back twice while charging: show " +
                                "battery. Face down with the screen off: the front glyph glows if you have " +
                                "unread notifications, the bottom line pulses gently while music plays, and " +
                                "pressing Volume Down twice blinks out the unread count.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = gesturesOn, onCheckedChange = {
                        gesturesOn = it
                        if (it) {
                            if (Build.VERSION.SDK_INT >= 33) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            GestureServiceControl.start(context)
                        } else {
                            GestureServiceControl.stop(context)
                        }
                    })
                }
                if (gesturesOn && !NotificationCounterService.Stats.isConnected(context)) {
                    AssistChip(
                        onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        },
                        label = { Text("Grant notification access for the unread-count effects") },
                        leadingIcon = { Icon(Icons.Filled.NotificationsActive, null) }
                    )
                }
            }
        }

        Text("Test the gesture actions manually:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                Glyph.showBattery(bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY))
            }) { Text("Battery") }
            OutlinedButton(onClick = {
                Glyph.showCount(NotificationCounterService.Stats.countActive(context).coerceAtMost(8))
            }) { Text("Unread count") }
        }
    }
}
