package eu.nakokot.glyphha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val token = Prefs.token(context)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Home Assistant", style = MaterialTheme.typography.titleMedium)
        Text(
            "Send a silent broadcast via the Companion app's notify service — this never shows a " +
                "notification. Use the device's notify.mobile_app_<device> service.",
            style = MaterialTheme.typography.bodySmall
        )
        ElevatedCard(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    "service: notify.mobile_app_<device>\n" +
                        "data:\n" +
                        "  message: command_broadcast_intent\n" +
                        "  title: eu.nakokot.glyphha.PULSE\n" +
                        "  data:\n" +
                        "    intent_package_name: eu.nakokot.glyphha\n" +
                        "    intent_extras: \"token:$token,group:round,value:100,ms:800\"",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Actions", style = MaterialTheme.typography.titleSmall)
        Text(
            "SET (group, value 0-100) · PULSE (group, value, ms) · OFF · " +
                "PLAY_ANIM (name: a saved animation from the Animations tab)",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Groups: ${Glyph.groups.keys.joinToString()}", style = MaterialTheme.typography.bodySmall)

        ElevatedCard(Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(
                    "intent_extras: \"token:$token,name:my_animation\"\n" +
                        "title: eu.nakokot.glyphha.PLAY_ANIM",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Token", style = MaterialTheme.typography.titleSmall)
        SelectionContainer { Text(token, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        var debugToasts by remember { mutableStateOf(Prefs.debugToasts(context)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Debug toasts for gestures", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Shows a toast for every detected tap/shake while tuning. Turn off once it feels right.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = debugToasts, onCheckedChange = { debugToasts = it; Prefs.setDebugToasts(context, it) })
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("About the gestures", style = MaterialTheme.typography.titleSmall)
        Text(
            "Gesture detection runs as a low-priority foreground service and reads the " +
                "accelerometer continuously, which costs roughly 1-3% battery per day. Shake and " +
                "tap thresholds are approximate — there's no dedicated tap sensor on this phone, " +
                "so accuracy will be lower than a Pixel's \"Quick Tap\".",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
