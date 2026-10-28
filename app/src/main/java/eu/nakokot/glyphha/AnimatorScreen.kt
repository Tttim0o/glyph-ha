package eu.nakokot.glyphha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatorScreen() {
    val context = LocalContext.current
    var animations by remember { mutableStateOf(GlyphAnim.load(context)) }
    var steps by remember { mutableStateOf(listOf<AnimStep>()) }
    var name by remember { mutableStateOf("") }
    var loops by remember { mutableFloatStateOf(1f) }

    var target by remember { mutableStateOf("white") }
    var percent by remember { mutableFloatStateOf(100f) }
    var ms by remember { mutableFloatStateOf(300f) }
    var targetMenu by remember { mutableStateOf(false) }

    fun refresh() { animations = GlyphAnim.load(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Animation composer", style = MaterialTheme.typography.titleMedium)
        Text(
            "Build a sequence of steps — each lights a zone or a single LED (1–16) at a brightness " +
                "for a duration — then save and play it, loop it, or trigger it from Home Assistant.",
            style = MaterialTheme.typography.bodySmall
        )

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = targetMenu, onExpandedChange = { targetMenu = it }) {
                    OutlinedTextField(
                        value = target, onValueChange = {}, readOnly = true,
                        label = { Text("Target") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                        GlyphAnim.targets().forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { target = t; targetMenu = false })
                        }
                    }
                }
                Text("Brightness — ${percent.toInt()}%")
                Slider(value = percent, onValueChange = { percent = it }, valueRange = 0f..100f)
                Text("Hold — ${ms.toInt()} ms")
                Slider(value = ms, onValueChange = { ms = it }, valueRange = 50f..5000f)
                Button(onClick = { steps = steps + AnimStep(target, percent.toInt(), ms.toInt()) }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add step")
                }
            }
        }

        if (steps.isNotEmpty()) {
            Text("Steps (${steps.size})", style = MaterialTheme.typography.titleSmall)
            steps.forEachIndexed { i, s ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${s.target} @ ${s.percent}% for ${s.ms}ms", Modifier.weight(1f))
                        IconButton(enabled = i > 0, onClick = {
                            steps = steps.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                        }) { Icon(Icons.Filled.ArrowUpward, "move up") }
                        IconButton(enabled = i < steps.size - 1, onClick = {
                            steps = steps.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                        }) { Icon(Icons.Filled.ArrowDownward, "move down") }
                        IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(i) } }) {
                            Icon(Icons.Filled.Delete, "remove")
                        }
                    }
                }
            }

            Text("Loops — ${loops.toInt()}")
            Slider(value = loops, onValueChange = { loops = it }, valueRange = 1f..20f, steps = 18)

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Animation name") },
                modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { GlyphAnim.play(GlyphAnimation(name.ifBlank { "preview" }, steps, loops.toInt())) }) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play")
                }
                OutlinedButton(onClick = { GlyphAnim.stop() }) {
                    Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop")
                }
                Button(enabled = name.isNotBlank(), onClick = {
                    GlyphAnim.save(context, GlyphAnimation(name, steps, loops.toInt()))
                    refresh(); steps = emptyList(); name = ""
                }) {
                    Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save")
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Saved animations", style = MaterialTheme.typography.titleMedium)
        animations.forEach { a ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                        Text("${a.steps.size} steps · ${a.loops}×", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { GlyphAnim.play(a) }) { Icon(Icons.Filled.PlayArrow, "play") }
                    IconButton(onClick = { GlyphAnim.delete(context, a.name); refresh() }) {
                        Icon(Icons.Filled.Delete, "delete")
                    }
                }
            }
        }
        if (animations.isEmpty()) Text("No saved animations yet.", style = MaterialTheme.typography.bodySmall)
    }
}
