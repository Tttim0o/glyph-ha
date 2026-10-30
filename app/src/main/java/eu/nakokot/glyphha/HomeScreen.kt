package eu.nakokot.glyphha

import android.Manifest
import android.content.Context
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
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var gesturesOn by remember { mutableStateOf(Prefs.gesturesEnabled(context)) }
    var flashOn by remember { mutableStateOf(Flash.on) }
    var shakeEffect by remember { mutableStateOf(Prefs.shakeEffect(context)) }
    var motionEffect by remember { mutableStateOf(Prefs.motionEffect(context)) }
    var hasMotion by remember { mutableStateOf(Prefs.motionTemplate(context) != null) }
    var recording by remember { mutableStateOf(false) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Poll the recording state so the button returns to normal once the window is saved.
    LaunchedEffect(recording) {
        if (recording) {
            while (GestureLearn.recording) delay(150)
            recording = false
            hasMotion = Prefs.motionTemplate(context) != null
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Glyphs", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Nothing Phone (1) glyphs and camera flash",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(if (gesturesOn) "Gestures on" else "Gestures off") })
                    AssistChip(
                        onClick = {},
                        label = { Text(if (NotificationCounterService.Stats.isConnected(context)) "Notifications" else "No notif access") }
                    )
                }
            }
        }

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
                Icon(Icons.Filled.FlashOn, null); Spacer(Modifier.width(6.dp)); Text("Glyph torch")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Camera flash", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Flash.toggle(context); flashOn = Flash.on }) {
                Icon(Icons.Filled.FlashOn, null); Spacer(Modifier.width(6.dp))
                Text(if (flashOn) "Flash off" else "Flash on")
            }
            OutlinedButton(onClick = { Flash.strobe(context, Flash.SOS_LIKE) }) { Text("SOS-like") }
            OutlinedButton(onClick = { Flash.strobe(context, Flash.BLINK_TWICE) }) { Text("Blink") }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Gestures", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Gesture detection")
                        Text(
                            "Shake twice: toggle torch. Tap the back twice while charging: battery. " +
                                "Face down with the screen off: unread-notification glow, music bar, " +
                                "Volume Down twice: unread count.",
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

        Text("Shake effect", style = MaterialTheme.typography.labelLarge)
        EffectChips(
            selected = shakeEffect,
            onSelect = { shakeEffect = it; Prefs.setShakeEffect(context, it) }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Custom motion", style = MaterialTheme.typography.titleMedium)
        Text(
            "Press record, then do your motion within ~2.5 s. The phone's motion is saved as a " +
                "template and replayed against every later motion; a close match runs the effect below. " +
                "Gestures must be enabled.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !recording,
                onClick = {
                    if (!gesturesOn) {
                        gesturesOn = true
                        GestureServiceControl.start(context)
                    }
                    GestureLearn.begin()
                    recording = true
                }
            ) {
                Icon(Icons.Filled.RadioButtonChecked, null); Spacer(Modifier.width(6.dp))
                Text(if (recording) "Recording…" else "Record motion")
            }
            OutlinedButton(enabled = hasMotion, onClick = {
                Prefs.setMotionTemplate(context, null)
                hasMotion = false
            }) { Text("Clear") }
        }
        Text(
            if (hasMotion) "A motion is saved." else "No motion saved yet.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Effect when the motion matches", style = MaterialTheme.typography.labelLarge)
        EffectChips(
            selected = motionEffect,
            onSelect = { motionEffect = it; Prefs.setMotionEffect(context, it) }
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Test the actions:", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                Glyph.showBattery(bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY))
            }) { Text("Battery") }
            OutlinedButton(onClick = {
                Glyph.showCount(NotificationCounterService.Stats.countActive(context).coerceAtMost(8))
            }) { Text("Unread count") }
        }
    }
}

/** Three-way choice used by both the shake and the custom-motion effects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectChips(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("glyph" to "Glyphs", "flash" to "Flash", "both" to "Both").forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) }
            )
        }
    }
}
