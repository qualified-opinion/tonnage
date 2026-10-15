package com.janreins.tonnage.timer

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The running rest countdown, shared by the workout screen and [com.janreins.tonnage.service.WorkoutService].
 * Times use the elapsed-realtime clock, which keeps counting while the phone sleeps. The service
 * does the actual waiting (holding a wake lock) and calls [finish] when time is up.
 */
object RestTimer {

    data class State(
        val endsAt: Long,
        val totalMs: Long,
        val exerciseName: String,
        val nextSet: String?,
    )

    /** Swappable for tests. */
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun start(seconds: Int, exerciseName: String, nextSet: String?) {
        if (seconds <= 0) {
            _state.value = null
            return
        }
        val total = seconds * 1000L
        _state.value = State(clock() + total, total, exerciseName, nextSet)
    }

    /** Adds (or with a negative value removes) time; going below zero ends the rest. */
    fun adjust(seconds: Int) {
        _state.update { current ->
            current ?: return@update null
            val endsAt = current.endsAt + seconds * 1000L
            if (endsAt <= clock()) null else current.copy(endsAt = endsAt, totalMs = (current.totalMs + seconds * 1000L).coerceAtLeast(1000L))
        }
    }

    fun skip() {
        _state.value = null
    }

    /** Ends [expected] if it is still the running countdown (not replaced by a newer one). */
    fun finish(expected: State) {
        _state.compareAndSet(expected, null)
    }

    fun remainingMs(state: State, now: Long = clock()): Long = (state.endsAt - now).coerceAtLeast(0)
}
