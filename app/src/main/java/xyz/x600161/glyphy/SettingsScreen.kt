package xyz.x600161.glyphy

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                        "  title: xyz.x600161.glyphy.PULSE\n" +
                        "  data:\n" +
                        "    intent_package_name: xyz.x600161.glyphy\n" +
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
                        "title: xyz.x600161.glyphy.PLAY_ANIM",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Updates", style = MaterialTheme.typography.titleSmall)
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var updateUrl by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Text(
            "Checks GitHub Releases only when you press the button. Nothing installs silently: " +
                "a new APK opens in your browser for you to confirm.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !checking, onClick = {
                checking = true
                updateStatus = null
                scope.launch {
                    val msg = withContext(Dispatchers.IO) {
                        try {
                            val r = UpdateChecker.check(context)
                            updateUrl = r.apkUrl ?: r.pageUrl
                            when {
                                r.updateAvailable -> "Version ${r.latestCode} is available (you have ${r.currentCode})."
                                else -> "You are up to date (version ${r.currentCode})."
                            }
                        } catch (e: Exception) {
                            updateUrl = null
                            "Update check failed: ${e.message ?: "network error"}"
                        }
                    }
                    updateStatus = msg
                    checking = false
                }
            }) { Text(if (checking) "Checking…" else "Check for updates") }
            if (updateUrl != null && updateStatus?.startsWith("Version") == true) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                }) { Text("Download") }
            }
        }
        updateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

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
                "so accuracy will be lower than a Pixel's \"Quick Tap\". The unread-notification " +
                "count comes from currently active, undismissed notifications (via notification " +
                "access), not a historical log.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("About the music pulse", style = MaterialTheme.typography.titleSmall)
        Text(
            "This is an ambient glow synced to a fixed rhythm while music plays face-down, not a " +
                "real audio-reactive visualizer. A true one would need to capture system audio " +
                "(AudioPlaybackCaptureConfiguration), which pops a one-time \"allow recording\" " +
                "system dialog and doesn't work with apps that block capture (Spotify, DRM audio).",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("About the Volume Down shortcut", style = MaterialTheme.typography.titleSmall)
        Text(
            "Pressing Volume Down twice while face-down with the screen off blinks out the unread " +
                "count. It's read at the raw input level (root), independently of the volume keys' " +
                "normal function. Volume Down + Power isn't used, since that combo is reserved " +
                "system-wide for the screenshot shortcut.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
