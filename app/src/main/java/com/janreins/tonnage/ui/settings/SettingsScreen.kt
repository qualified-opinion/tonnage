package com.janreins.tonnage.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.domain.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(defaultRestSeconds: Int, onDefaultRestChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            var open by remember { mutableStateOf(false) }
            Box {
                ListItem(
                    modifier = Modifier.clickable { open = true },
                    leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null) },
                    headlineContent = { Text("Default rest timer") },
                    supportingContent = { Text("Used for exercises you add to a workout") },
                    trailingContent = { Text(Format.restSeconds(defaultRestSeconds), color = MaterialTheme.colorScheme.primary) },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    SettingsStore.REST_OPTIONS.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text(Format.restSeconds(seconds)) },
                            onClick = { open = false; onDefaultRestChange(seconds) },
                        )
                    }
                }
            }
            Text(
                "Units, backup and Hevy import will be added here in later updates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
