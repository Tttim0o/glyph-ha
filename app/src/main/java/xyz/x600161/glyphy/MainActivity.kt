package xyz.x600161.glyphy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlyphTheme {
                var tab by remember { mutableIntStateOf(0) }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Glyph HA") }) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Home, "Home") }, label = { Text("Home") })
                            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.MovieFilter, "Animations") }, label = { Text("Animate") })
                            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Settings, "Settings") }, label = { Text("Settings") })
                        }
                    }
                ) { padding ->
                    Surface(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> HomeScreen()
                            1 -> AnimatorScreen()
                            else -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
