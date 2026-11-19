package `in`.qaida.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import `in`.qaida.app.MainActivity
import `in`.qaida.app.QaidaApp
import `in`.qaida.app.R
import `in`.qaida.app.domain.Progression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The one optional notification: off by default, at a time the learner picks, a single plain line saying
 * how many letters are due. No guilt framing, no emoji, no second reminder, and nothing at all when
 * nothing is due. An inexact daily alarm: battery matters more than the minute.
 */
object Reminder {
    private const val CHANNEL = "reminder"
    private const val NOTIFICATION_ID = 1

    fun schedule(context: Context, minuteOfDay: Int) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarms(context).setInexactRepeating(AlarmManager.RTC, next.timeInMillis, AlarmManager.INTERVAL_DAY, pending(context))
    }

    fun cancel(context: Context) = alarms(context).cancel(pending(context))

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context) {
        if (!canNotify(context)) return
        val c = (context.applicationContext as QaidaApp).container
        c.contentReady.await()
        val now = c.clock()
        val catalog = c.contentRepository.catalog()
        val states = c.progressRepository.reviewStates()
        val unlocked = c.progressRepository.refreshUnlockedTiers(catalog, states, now)
        val due = Progression.lettersNeedingReview(Progression.status(catalog, states, unlocked, now))
        if (due == 0) return

        val text = c.localizedResources().getQuantityString(R.plurals.reminder_letters_due, due, due)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, c.localizedResources().getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        @Suppress("MissingPermission") // checked by canNotify
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Reminder.post(context) } finally { result.finish() }
        }
    }
}

/** Alarms do not survive a reboot; put the reminder back if the learner has it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = (context.applicationContext as QaidaApp).container.settingsRepository.current()
                if (s.reminderEnabled) Reminder.schedule(context, s.reminderMinuteOfDay)
            } finally {
                result.finish()
            }
        }
    }
}
