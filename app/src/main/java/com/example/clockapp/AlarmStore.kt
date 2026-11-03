package com.example.clockapp

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import java.util.Calendar

data class AlarmItem(val id: Int, val hour: Int, val minute: Int, val repeatDaily: Boolean, val enabled: Boolean)

object AlarmStore {
    private fun prefs(c: Context) = c.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun load(c: Context): List<AlarmItem> =
        prefs(c).getString("data", "")!!.split(";").filter { it.isNotBlank() }.map {
            val p = it.split(",")
            AlarmItem(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toBoolean(), p[4].toBoolean())
        }

    fun save(c: Context, list: List<AlarmItem>) {
        prefs(c).edit().putString("data", list.joinToString(";") {
            "${it.id},${it.hour},${it.minute},${it.repeatDaily},${it.enabled}"
        }).apply()
    }

    private fun nextTrigger(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun pi(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context, a: AlarmItem) {
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(a.hour, a.minute), show), pi(c, a.id))
    }

    fun cancel(c: Context, a: AlarmItem) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, a.id))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val list = AlarmStore.load(c)
        val a = list.find { it.id == id } ?: return
        val nm = c.getSystemService(NotificationManager::class.java)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ch = NotificationChannel("alarm", "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(String.format("%02d:%02d", a.hour, a.minute))
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM).build()
        nm.notify(id, n)
        if (a.repeatDaily) AlarmStore.schedule(c, a)
        else AlarmStore.save(c, list.map { if (it.id == id) it.copy(enabled = false) else it })
    }
}
