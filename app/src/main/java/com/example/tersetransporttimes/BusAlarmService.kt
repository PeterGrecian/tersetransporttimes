package com.example.tersetransporttimes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class BusAlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "bus_alarm_channel"
        const val ALARM_CHANNEL_ID = "bus_alarm_ringing_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.example.tersetransporttimes.STOP_ALARM"

        const val EXTRA_STOP = "stop"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_INDEX = "index"
        const val EXTRA_INITIAL_SECONDS = "initial_seconds"

        const val CHECK_INTERVAL_MS = 30_000L // 30 seconds

        var isRunning = false
        var currentRingtone: Ringtone? = null

        fun stopAlarm() {
            currentRingtone?.stop()
            currentRingtone = null
        }

        fun isRinging(): Boolean {
            return try {
                currentRingtone?.isPlaying == true
            } catch (e: Exception) {
                false
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var checkJob: Job? = null

    private var stop = "parklands"
    private var direction = "inbound"
    private var index = 0
    private var lastAlarmThreshold = Int.MAX_VALUE
    private var startedAtTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        stop = intent?.getStringExtra(EXTRA_STOP) ?: "parklands"
        direction = intent?.getStringExtra(EXTRA_DIRECTION) ?: "inbound"
        index = intent?.getIntExtra(EXTRA_INDEX, 0) ?: 0
        val initialSeconds = intent?.getIntExtra(EXTRA_INITIAL_SECONDS, 600) ?: 600

        lastAlarmThreshold = calculateInitialAlarmThreshold(initialSeconds)
        startedAtTime = System.currentTimeMillis()

        val notification = createNotification("Bus alarm armed")
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        startChecking()

        return START_STICKY
    }

    private fun startChecking() {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            while (isActive) {
                try {
                    val seconds = fetchBusTime()
                    if (seconds != null) {
                        // Update notification with current time
                        val minutes = seconds / 60
                        updateNotification("Bus in ~$minutes min")

                        // Check if bus has arrived (under 3 minutes)
                        if (seconds < ALARM_INTERVAL_SECONDS) {
                            // Bus is arriving, sound final alarm and stop
                            playAlarm("Bus arriving in ${minutes} min")
                            delay(5000) // Let alarm play for 5 seconds
                            stopSelf()
                            return@launch
                        }

                        // Check if we've crossed a 3-minute threshold
                        // Add 15-second grace period to prevent immediate alarm after arming
                        val currentThreshold = (seconds / ALARM_INTERVAL_SECONDS) * ALARM_INTERVAL_SECONDS
                        val timeSinceStart = System.currentTimeMillis() - startedAtTime
                        if (currentThreshold < lastAlarmThreshold && timeSinceStart > 15_000) {
                            playAlarm("Bus arriving in $minutes min")
                            lastAlarmThreshold = currentThreshold
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchBusTime(): Int? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$API_BASE_URL/t3?stop=$stop")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: "{}")

                val directionData = if (direction == "inbound" && json.has("inbound")) {
                    json.getJSONObject("inbound")
                } else if (direction == "outbound" && json.has("outbound")) {
                    json.getJSONObject("outbound")
                } else {
                    return@withContext null
                }

                val secondsArray = directionData.getJSONArray("seconds")
                if (index < secondsArray.length()) {
                    secondsArray.getInt(index)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun playAlarm(message: String = "Your bus is arriving!") {
        try {
            stopAlarm()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            currentRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            currentRingtone?.play()
            showAlarmNotification(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Low priority channel for normal armed state
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bus Alarm",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when bus alarm is active"
        }
        notificationManager.createNotificationChannel(channel)

        // High priority channel for when alarm is ringing
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Bus Alarm Ringing",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows when bus is arriving"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }
        notificationManager.createNotificationChannel(alarmChannel)
    }

    private fun createNotification(text: String): Notification {
        val stopIntent = Intent(this, BusAlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("T3")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showAlarmNotification(text: String) {
        val stopIntent = Intent(this, BusAlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("🚌 Bus Arriving!")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        checkJob?.cancel()
        serviceScope.cancel()
        stopAlarm()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
