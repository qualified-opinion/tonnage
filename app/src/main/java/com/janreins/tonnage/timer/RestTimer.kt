package com.janreins.tonnage.timer

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var store: RestTimerStore? = null

    @Synchronized
    fun attach(context: Context) {
        store = RestTimerStore(context.applicationContext.getSharedPreferences("rest_timer", Context.MODE_PRIVATE))
    }

    /** Keep an in-process timer; only recover persisted state when memory has none. */
    @Synchronized
    fun restore(): State? {
        if (_state.value != null && remainingMs(_state.value!!) == 0L) publish(null)
        if (_state.value == null) _state.value = store?.restore()
        return _state.value
    }

    private fun publish(state: State?) {
        store?.save(state)
        _state.value = state
    }

    @Synchronized
    fun start(seconds: Int, exerciseName: String, nextSet: String?) {
        if (seconds <= 0) {
            publish(null)
            return
        }
        val total = seconds * 1000L
        publish(State(clock() + total, total, exerciseName, nextSet))
    }

    /** Adds (or with a negative value removes) time; going below zero ends the rest. */
    @Synchronized
    fun adjust(seconds: Int) {
        val current = _state.value ?: return
        val endsAt = current.endsAt + seconds * 1000L
        publish(if (endsAt <= clock()) null else current.copy(endsAt = endsAt, totalMs = (current.totalMs + seconds * 1000L).coerceAtLeast(1000L)))
    }

    @Synchronized
    fun skip() = publish(null)

    /** Ends [expected] only once; a stale service or screen cannot clear a newer checkpoint. */
    @Synchronized
    fun finish(expected: State): Boolean {
        if (_state.value != expected) return false
        publish(null)
        return true
    }

    fun remainingMs(state: State, now: Long = clock()): Long = (state.endsAt - now).coerceAtLeast(0)
}
