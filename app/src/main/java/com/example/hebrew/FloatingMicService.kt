package com.example.hebrew

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class FloatingMicService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> floatingView?.visibility = View.VISIBLE
            ACTION_HIDE -> floatingView?.visibility = View.GONE
        }
        return START_STICKY
    }

    override fun onDestroy() {
        floatingView?.let { windowManager?.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "floating_mic"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Плавающая кнопка", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Кнопка микрофона активна")
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val screenHeight = resources.displayMetrics.heightPixels
        val savedY = prefs.getInt(PREF_Y, screenHeight / 2)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = savedY
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_button, null)
        disableSoundEffects(floatingView)
        setupTouchHandler()
        windowManager?.addView(floatingView, params)
    }

    private fun disableSoundEffects(view: View?) {
        if (view == null) return
        view.isSoundEffectsEnabled = false
        view.isHapticFeedbackEnabled = false
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) disableSoundEffects(view.getChildAt(i))
        }
    }

    private fun setupTouchHandler() {
        val touchSlop = 8 * resources.displayMetrics.density
        var isDrag = false
        var downY = 0
        var downRawY = 0f

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDrag = false
                    downY = params.y
                    downRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDrag && Math.abs(event.rawY - downRawY) > touchSlop) isDrag = true
                    if (isDrag) {
                        params.y = (downY + event.rawY - downRawY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDrag) {
                        prefs.edit().putInt(PREF_Y, params.y).apply()
                    } else {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            action = ACTION_OPEN_VOICE
                        })
                    }
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.example.hebrew.SHOW_FLOATING"
        const val ACTION_HIDE = "com.example.hebrew.HIDE_FLOATING"
        const val ACTION_OPEN_VOICE = "com.example.hebrew.OPEN_VOICE"
        private const val NOTIF_ID = 9001
        private const val PREFS_NAME = "floating_mic_prefs"
        private const val PREF_Y = "y"
    }
}
