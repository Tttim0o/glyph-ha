package eu.nakokot.glyphha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = Prefs.token(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Glyph HA", style = MaterialTheme.typography.headlineMedium)
                        Glyph.groups.keys.forEach { g ->
                            var v by remember { mutableFloatStateOf(0f) }
                            Text("$g: ${v.toInt()} %")
                            Slider(value = v, onValueChange = { v = it }, valueRange = 0f..100f,
                                onValueChangeFinished = { Glyph.set(g, v.toInt()) })
                        }
                        Button(onClick = { Glyph.off() }) { Text("Off") }
                        Button(onClick = { Glyph.pulse("white", 100, 500) }) { Text("Pulse") }
                        Spacer(Modifier.height(12.dp))
                        Text("Home Assistant", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer {
                            Text(
                                "service: notify.mobile_app_<device>\ndata:\n  message: command_broadcast_intent\n" +
                                    "  title: eu.nakokot.glyphha.PULSE\n  data:\n    intent_package_name: eu.nakokot.glyphha\n" +
                                    "    intent_extras: \"token:$token,group:round,value:100,ms:800\"\n\n" +
                                    "Actions: SET (group,value 0-100), PULSE (group,value,ms), OFF\n" +
                                    "Groups: ${Glyph.groups.keys.joinToString()}\nToken: $token",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
