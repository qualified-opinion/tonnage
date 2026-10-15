package com.janreins.tonnage.timer

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the ongoing-workout notification shows. Set by the workout screen while a workout is
 * active; null means no workout and the service stops.
 */
object WorkoutSession {
    data class Info(val title: String, val startedAt: Long, val completedSets: Int)

    val info = MutableStateFlow<Info?>(null)

    /**
     * True once the active workout was started or resumed in this app process. After the process
     * dies (app killed, phone restarted) it is false again, so the app asks to resume or discard.
     */
    @Volatile
    var confirmedInThisProcess: Boolean = false
}
