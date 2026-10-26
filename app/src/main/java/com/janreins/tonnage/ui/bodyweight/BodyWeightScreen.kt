package com.janreins.tonnage.ui.bodyweight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.BodyWeight
import com.janreins.tonnage.domain.BodyWeights
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.domain.SetInput
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

/** Enter today's (or an earlier day's) body weight, and see or delete earlier entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyWeightScreen(
    entries: List<BodyWeight>,
    today: LocalDate,
    onSave: (LocalDate, Double?) -> Boolean,
    onDelete: (BodyWeight) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BodyWeight?>(null) }
    val date = LocalDate.ofEpochDay(epochDay)
    val weight = Format.parseDecimal(text)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Body weight") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input -> SetInput.accept(SetInput.Kind.WEIGHT, text, input)?.let { text = it } },
                        label = { Text("Weight (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { picking = true }) {
                        Text(if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())))
                    }
                }
                Button(
                    onClick = { if (onSave(date, weight)) text = "" },
                    enabled = BodyWeights.canSave(date, weight, today),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text("Save")
                }
                if (entries.any { it.epochDay == epochDay }) {
                    Text(
                        "This replaces the entry for ${date.format(longDate)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(top = 12.dp))
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "No body weight entries yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text("${entry.date.format(longDate)} · ${BodyWeights.kg(entry.weightKg)}") },
                    trailingContent = {
                        IconButton(onClick = { deleting = entry }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete the entry for ${entry.date.format(longDate)}")
                        }
                    },
                )
            }
        }
    }

    if (picking) {
        val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1900..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Math.floorDiv(it, 86_400_000L) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this entry?") },
            text = { Text("${entry.date.format(longDate)} · ${BodyWeights.kg(entry.weightKg)}") },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}
