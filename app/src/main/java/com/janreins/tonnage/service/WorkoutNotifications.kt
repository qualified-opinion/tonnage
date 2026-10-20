package com.janreins.tonnage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.janreins.tonnage.MainActivity
import com.janreins.tonnage.R
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.timer.WorkoutSession

object WorkoutNotifications {
    const val CHANNEL_WORKOUT = "workout"
    const val CHANNEL_REST_DONE = "rest_done"
    const val ID_WORKOUT = 1
    const val ID_REST_DONE = 2

    private val REST_DONE_VIBRATION = longArrayOf(0, 400, 200, 400)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORKOUT, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running workout and rest timer"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REST_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts you when the rest timer runs out"
                enableVibration(true)
                vibrationPattern = REST_DONE_VIBRATION
            },
        )
    }

    /** Opens the app the way the launcher does, bringing the existing screen to the front. */
    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, WorkoutService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun ongoing(context: Context, info: WorkoutSession.Info?, rest: RestTimer.State?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_tonnage)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (rest != null) {
            val remaining = RestTimer.remainingMs(rest)
            builder
                .setContentTitle("Rest · ${rest.exerciseName}")
                .setContentText(rest.nextSet?.let { "Next: $it" } ?: "Rest timer running")
                .setWhen(System.currentTimeMillis() + remaining)
                .setChronometerCountDown(true)
                .addAction(0, "+15s", serviceAction(context, WorkoutService.ACTION_ADD_15, 1))
                .addAction(0, "Skip", serviceAction(context, WorkoutService.ACTION_SKIP, 2))
                .addAction(0, "Open", openAppIntent(context))
        } else {
            val sets = info?.completedSets ?: 0
            builder
                .setContentTitle(info?.title ?: "Workout in progress")
                .setContentText(if (sets == 1) "1 set done" else "$sets sets done")
                .setWhen(info?.startedAt ?: System.currentTimeMillis())
                .setChronometerCountDown(false)
        }
        return builder.build()
    }

    fun restDone(context: Context, rest: RestTimer.State): Notification =
        NotificationCompat.Builder(context, CHANNEL_REST_DONE)
            .setSmallIcon(R.drawable.ic_stat_tonnage)
            .setContentTitle("Rest over")
            .setContentText(rest.nextSet?.let { "Next: $it" } ?: "Time for your next set")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(REST_DONE_VIBRATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    fun restAlertsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_REST_DONE)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Both the service and the in-app fallback use the same alarm vibration. */
    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        } ?: return
        val effect = VibrationEffect.createWaveform(REST_DONE_VIBRATION, -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    /** Posts or updates a notification; silently does nothing if notifications are not allowed. */
    fun post(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(notification.channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
