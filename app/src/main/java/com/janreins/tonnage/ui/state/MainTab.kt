package com.janreins.tonnage.ui.state

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(val label: String, val icon: ImageVector) {
    WORKOUT("Workout", Icons.Filled.FitnessCenter),
    HISTORY("History", Icons.Filled.History),
    EXERCISES("Exercises", Icons.AutoMirrored.Filled.ViewList),
    PROGRESS("Progress", Icons.AutoMirrored.Filled.ShowChart),
    MORE("More", Icons.Filled.MoreHoriz),
}
