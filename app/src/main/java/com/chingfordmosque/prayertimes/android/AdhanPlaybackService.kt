package com.chingfordmosque.prayertimes.android

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.chingfordmosque.prayertimes.android.platform.AlarmManagerAdhanPort
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

class AdhanPlaybackService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var mediaPlayer: MediaPlayer? = null
    private val okHttpClient = OkHttpClient()
    private var playDua: Boolean = false
    private var shortAzaan: Boolean = false
    private var originalVolume: Int = -1

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                stopPlaybackAndService()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_PLAYBACK) {
            stopPlaybackAndService()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(AlarmManagerAdhanPort.EXTRA_PRAYER_NAME) ?: "Prayer"
        playDua = intent?.getBooleanExtra(AlarmManagerAdhanPort.EXTRA_PLAY_DUA, false) ?: false
        shortAzaan = intent?.getBooleanExtra(AlarmManagerAdhanPort.EXTRA_SHORT_AZAAN, false) ?: false
        val azaanVolume = intent?.getIntExtra(AlarmManagerAdhanPort.EXTRA_AZAAN_VOLUME, 100) ?: 100
        
        applyVolumeOverride(azaanVolume)

        // Start foreground immediately to prevent being killed
        startForegroundServiceNotification(prayerName, "Checking live stream...")

        serviceScope.launch {
            val liveUrl = checkLiveStreamUrl()
            withContext(Dispatchers.Main) {
                if (liveUrl != null) {
                    startForegroundServiceNotification(prayerName, "Playing Live Azaan")
                    playAudio(Uri.parse(liveUrl))
                } else {
                    startForegroundServiceNotification(prayerName, "Playing Offline Adhan")
                    playOfflineAdhan()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun applyVolumeOverride(azaanVolume: Int) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = ((azaanVolume / 100f) * maxVolume).roundToInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } catch (e: Exception) {
            // Ignore if permission denied or device restriction
        }
    }

    private fun startForegroundServiceNotification(prayerName: String, status: String) {
        Notifications.createPlaybackChannel(this)

        val stopIntent = Intent(this, AdhanPlaybackService::class.java).apply {
            action = ACTION_STOP_PLAYBACK
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Notifications.LIVE_AZAAN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$prayerName \u2014 Adhan")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun checkLiveStreamUrl(): String? {
        try {
            val request = Request.Builder()
                .url("https://emasjidlive.co.uk/miniplayer/chingfordmasjid")
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                if (html.contains("Currently Offline", ignoreCase = true)) {
                    return null
                }
                
                val srcRegex = """<source[^>]*src=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                val audioSrcRegex = """<audio[^>]*src=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                
                val srcMatch = srcRegex.find(html) ?: audioSrcRegex.find(html)
                if (srcMatch != null) {
                    return srcMatch.groupValues[1]
                }
                
                return "https://emasjidlive.co.uk/listen/chingfordmasjid"
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun playOfflineAdhan() {
        val rawName = if (shortAzaan) "adhan_short" else "adhan"
        val rawId = resources.getIdentifier(rawName, "raw", packageName)
        val soundUri: Uri = if (rawId != 0) {
            Uri.parse("android.resource://$packageName/$rawId")
        } else {
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }
        playAudio(soundUri)
    }

    private fun playAudio(uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AdhanPlaybackService, uri)
                setOnCompletionListener { 
                    if (playDua && !isLiveStream(uri)) {
                        playDua()
                    } else {
                        stopPlaybackAndService()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stopPlaybackAndService()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                }
            }
        } catch (e: Exception) {
            stopPlaybackAndService()
        }
    }

    private fun isLiveStream(uri: Uri): Boolean {
        return uri.toString().startsWith("http")
    }

    private fun playDua() {
        val rawId = resources.getIdentifier("dua", "raw", packageName)
        if (rawId == 0) {
            stopPlaybackAndService()
            return
        }
        val soundUri = Uri.parse("android.resource://$packageName/$rawId")
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AdhanPlaybackService, soundUri)
                setOnCompletionListener { 
                    stopPlaybackAndService() 
                }
                setOnErrorListener { _, _, _ ->
                    stopPlaybackAndService()
                    true
                }
                prepareAsync()
                setOnPreparedListener {
                    start()
                }
            }
        } catch (e: Exception) {
            stopPlaybackAndService()
        }
    }

    private fun stopPlaybackAndService() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
        }
        
        if (originalVolume != -1) {
            try {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            } catch (e: Exception) {
                // Ignore
            }
            originalVolume = -1
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(volumeReceiver)
        serviceJob.cancel()
        stopPlaybackAndService()
    }

    companion object {
        const val ACTION_STOP_PLAYBACK = "com.chingfordmosque.prayertimes.action.STOP_PLAYBACK"
        private const val NOTIFICATION_ID = 1001
        
        fun start(context: Context, prayerName: String, playDua: Boolean = false, azaanVolume: Int = 100, shortAzaan: Boolean = false) {
            val intent = Intent(context, AdhanPlaybackService::class.java).apply {
                putExtra(AlarmManagerAdhanPort.EXTRA_PRAYER_NAME, prayerName)
                putExtra(AlarmManagerAdhanPort.EXTRA_PLAY_DUA, playDua)
                putExtra(AlarmManagerAdhanPort.EXTRA_AZAAN_VOLUME, azaanVolume)
                putExtra(AlarmManagerAdhanPort.EXTRA_SHORT_AZAAN, shortAzaan)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
