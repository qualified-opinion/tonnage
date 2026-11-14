package com.janreins.tonnage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** "November 2026", in the phone's language. */
@Composable
fun monthName(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", LocalLocale.current.platformLocale))
        .replaceFirstChar { it.uppercase() }

/**
 * A Monday-to-Sunday calendar of [month] with each training day filled in green; [today] (when in
 * the month) is ringed. TalkBack reads it as one line listing the training days.
 */
@Composable
fun MonthCalendar(month: YearMonth, workoutDays: Map<Int, Int>, today: LocalDate?, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val offset = month.atDay(1).dayOfWeek.value - 1
    val length = month.lengthOfMonth()
    val rows = (offset + length + 6) / 7
    val name = monthName(month)
    val trained = workoutDays.keys.sorted()
    val description = if (trained.isEmpty()) "$name: no workouts" else "$name: trained on the ${trained.joinToString()}"
    Column(modifier.clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        repeat(rows) { row ->
            Row {
                repeat(7) { column ->
                    val day = row * 7 + column - offset + 1
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..length) {
                            DayCell(day, workouts = workoutDays[day] ?: 0, isToday = today == month.atDay(day))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, workouts: Int, isToday: Boolean) {
    val trained = workouts > 0
    val ring = if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape) else Modifier
    Box(
        Modifier
            .size(32.dp)
            .background(if (trained) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .then(ring),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (trained) FontWeight.SemiBold else FontWeight.Normal,
            color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
