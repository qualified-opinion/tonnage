package com.janreins.tonnage.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.janreins.tonnage.domain.Achievement
import com.janreins.tonnage.domain.AchievementKind
import com.janreins.tonnage.domain.MilestoneKind

/** One picture per kind of milestone, so the list reads at a glance. */
fun milestoneIcon(kind: MilestoneKind): ImageVector = when (kind) {
    MilestoneKind.WORKOUTS -> Icons.Filled.CheckCircle
    MilestoneKind.VOLUME -> Icons.AutoMirrored.Filled.TrendingUp
    MilestoneKind.STREAK -> Icons.Filled.LocalFireDepartment
    MilestoneKind.LIFT -> Icons.Filled.FitnessCenter
    MilestoneKind.STRENGTH -> Icons.Filled.MonitorWeight
    MilestoneKind.REPS -> Icons.Filled.Repeat
    MilestoneKind.VARIETY -> Icons.Filled.Explore
    MilestoneKind.TIME -> Icons.Filled.Timer
    MilestoneKind.MONTH -> Icons.Filled.CalendarMonth
}

fun achievementIcon(achievement: Achievement): ImageVector = when (achievement.kind) {
    AchievementKind.RECORD -> Icons.Filled.EmojiEvents
    AchievementKind.MILESTONE -> achievement.milestoneKind?.let(::milestoneIcon) ?: Icons.Filled.EmojiEvents
    AchievementKind.WORKOUT_BEST -> Icons.Filled.Bolt
    AchievementKind.NEW_EXERCISE -> Icons.Filled.FiberNew
}
