package com.janreins.tonnage.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.timer.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that runs while a workout is active. It keeps the ongoing-workout
 * notification up to date and counts down the rest timer with the screen off, holding a partial
 * wake lock only while a rest is running, then vibrates and notifies when the rest is over.
 */
class WorkoutService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WorkoutNotifications.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            WorkoutNotifications.ID_WORKOUT,
            WorkoutNotifications.ongoing(this, WorkoutSession.info.value, RestTimer.state.value),
            type,
        )

        scope.launch {
            // The workout screen may publish the session info just after starting the service, so
            // only a change from a workout to none means the workout ended.
            var hadWorkout = false
            combine(WorkoutSession.info, RestTimer.state) { info, rest -> info to rest }.collect { (info, rest) ->
                if (info == null) {
                    if (hadWorkout) stopSelfCompletely()
                } else {
                    hadWorkout = true
                    WorkoutNotifications.post(
                        this@WorkoutService,
                        WorkoutNotifications.ID_WORKOUT,
                        WorkoutNotifications.ongoing(this@WorkoutService, info, rest),
                    )
                }
            }
        }

        scope.launch {
            RestTimer.state.collectLatest { rest ->
                if (rest == null) {
                    releaseWakeLock()
                    return@collectLatest
                }
                val remaining = RestTimer.remainingMs(rest)
                acquireWakeLock(remaining + WAKE_LOCK_MARGIN_MS)
                delay(remaining)
                onRestFinished(rest)
                RestTimer.finish(rest)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_ADD_15 -> RestTimer.adjust(15)
            ACTION_SKIP -> RestTimer.skip()
        }
        // A button on a leftover notification after the workout ended: nothing to keep running.
        if (action != null && WorkoutSession.info.value == null) stopSelfCompletely()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun onRestFinished(rest: RestTimer.State) {
        vibrate()
        WorkoutNotifications.post(this, WorkoutNotifications.ID_REST_DONE, WorkoutNotifications.restDone(this, rest))
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    }

    private fun acquireWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tonnage:rest-timer").apply {
            setReferenceCounted(false)
            acquire(timeoutMs)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopSelfCompletely() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_ADD_15 = "com.janreins.tonnage.action.REST_ADD_15"
        const val ACTION_SKIP = "com.janreins.tonnage.action.REST_SKIP"
        private const val WAKE_LOCK_MARGIN_MS = 10_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
            } catch (_: IllegalStateException) {
                // Not allowed from the background; the app starts it again when it is next opened.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
