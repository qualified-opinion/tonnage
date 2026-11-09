package com.janreins.tonnage.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.domain.Format
import com.janreins.tonnage.timer.RestTimer
import kotlinx.coroutines.delay

/**
 * Shown above the tabs while a workout is running and another tab is open, so the workout and
 * its rest countdown can't be forgotten. Tapping it, or Resume, goes back to the workout.
 */
@Composable
fun WorkoutInProgressBar(workout: Workout, rest: RestTimer.State?, onResume: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restNow by remember { mutableLongStateOf(RestTimer.clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            restNow = RestTimer.clock()
            delay(1000)
        }
    }
    val elapsed = Format.duration(now - workout.startedAt)
    val restLeft = rest?.let { RestTimer.remainingMs(it, restNow) }?.takeIf { it > 0 }
    val detail = restLeft?.let { "$elapsed · rest ${Format.duration(it)} left" } ?: elapsed
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Back to workout", onClick = onResume)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(
                    "${workout.title} in progress",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onResume) { Text("Resume") }
        }
    }
}
