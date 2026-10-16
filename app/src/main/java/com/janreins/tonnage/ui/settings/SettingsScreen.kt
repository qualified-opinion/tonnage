package com.janreins.tonnage.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.data.settings.ThemeMode
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.ui.components.BodyMapArt
import com.janreins.tonnage.ui.data.BackupState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the More tab's data section can do. */
class DataActions(
    val onImportHevy: (Uri) -> Unit,
    val onSaveBackup: (Uri) -> Unit,
    val onOpenBackup: (Uri) -> Unit,
    val onConfirmRestore: () -> Unit,
    val onDismissBackup: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    defaultRestSeconds: Int,
    onDefaultRestChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    backupState: BackupState?,
    data: DataActions,
    modifier: Modifier = Modifier,
) {
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(data.onImportHevy) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(data.onSaveBackup)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(data.onOpenBackup) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
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
            var themeOpen by remember { mutableStateOf(false) }
            Box {
                ListItem(
                    modifier = Modifier.clickable { themeOpen = true },
                    leadingContent = { Icon(Icons.Filled.Contrast, contentDescription = null) },
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text("Light, dark, or the same as your phone") },
                    trailingContent = { Text(themeMode.label, color = MaterialTheme.colorScheme.primary) },
                )
                DropdownMenu(expanded = themeOpen, onDismissRequest = { themeOpen = false }) {
                    ThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = { themeOpen = false; onThemeModeChange(mode) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Your data",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                modifier = Modifier.clickable { importFile.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                headlineContent = { Text("Import from Hevy") },
                supportingContent = { Text("In Hevy: Settings → Export & Import Data → Export Workouts, then pick the CSV file here") },
            )
            ListItem(
                modifier = Modifier.clickable {
                    saveFile.launch("tonnage-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json")
                },
                leadingContent = { Icon(Icons.Filled.Save, contentDescription = null) },
                headlineContent = { Text("Back up to a file") },
                supportingContent = { Text("Saves your workouts, routines and custom exercises. Keep it in Drive or email it to yourself.") },
            )
            ListItem(
                modifier = Modifier.clickable { openFile.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                leadingContent = { Icon(Icons.Filled.Restore, contentDescription = null) },
                headlineContent = { Text("Restore from a backup") },
                supportingContent = { Text("Replaces everything in Tonnage with the backup's contents") },
            )
            HorizontalDivider()
            Text(
                "Credits",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("Body map artwork") },
                supportingContent = { Text(BodyMapArt.CREDIT) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                headlineContent = { Text("Exercise library") },
                supportingContent = { Text("From free-exercise-db by yuhonas, public domain.") },
            )
        }
    }

    BackupDialog(backupState, data)
}

@Composable
private fun BackupDialog(state: BackupState?, data: DataActions) {
    when (state) {
        null -> Unit
        BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Working…") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
        is BackupState.Saved -> Info("Backup saved", "Saved ${count(state.workouts, "workout")}, plus your routines and custom exercises.", data)
        is BackupState.Restored -> Info("Backup restored", "Restored ${count(state.workouts, "workout")}, plus routines and custom exercises.", data)
        is BackupState.Failed -> Info("Something went wrong", state.message, data)
        is BackupState.ConfirmRestore -> {
            val c = state.contents
            val made = if (c.exportedAt > 0) SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(c.exportedAt)) else "an unknown date"
            AlertDialog(
                onDismissRequest = data.onDismissBackup,
                title = { Text("Restore this backup?") },
                text = {
                    Text(
                        "Made $made: ${count(c.workouts.size, "workout")}, ${count(c.routines.size, "routine")} and " +
                            "${count(c.customExercises.size, "custom exercise")}.\n\n" +
                            "Everything currently in Tonnage will be replaced. Back up first if you might want it.",
                    )
                },
                confirmButton = { TextButton(onClick = data.onConfirmRestore) { Text("Replace and restore") } },
                dismissButton = { TextButton(onClick = data.onDismissBackup) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Info(title: String, body: String, data: DataActions) {
    AlertDialog(
        onDismissRequest = data.onDismissBackup,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = data.onDismissBackup) { Text("OK") } },
    )
}

private fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
