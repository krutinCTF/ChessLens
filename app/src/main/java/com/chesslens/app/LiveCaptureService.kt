package com.chesslens.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.view.Window
import android.view.WindowManager.LayoutParams
import android.content.Context

class LiveCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var overlay: View? = null
    private var frameCount = 0
    private var lastFrameAt = 0L
    private var statusText: TextView? = null
    private lateinit var windowManager: WindowManager
    private var worker: HandlerThread? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startCapture(resultCode, data)
        showOverlay()
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            worker = HandlerThread("ChessLensCapture").also { it.start() }
            val handler = Handler(worker!!.looper)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader!!.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    frameCount++
                    lastFrameAt = System.currentTimeMillis()
                    image.close()
                    if (frameCount % 30 == 0) {
                        statusText?.post {
                            statusText?.text = "Захват активен · кадров: $frameCount\nРаспознавание доски — следующий этап"
                        }
                    }
                }
            }, handler)

            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, handler)

            virtualDisplay = projection?.createVirtualDisplay(
                "ChessLensLive",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                handler
            )
        } catch (_: SecurityException) {
            stopSelf()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun showOverlay() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.argb(235, 28, 25, 43))
        }
        val title = TextView(this).apply {
            text = "CHESSLENS LIVE"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        statusText = TextView(this).apply {
            text = "Подключаем захват экрана…"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            setPadding(0, 8, 0, 8)
        }
        val stop = Button(this).apply {
            text = "Остановить"
            setOnClickListener { stopSelf() }
        }
        panel.addView(title)
        panel.addView(statusText)
        panel.addView(stop)

        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) LayoutParams.TYPE_APPLICATION_OVERLAY else LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 140
        }

        panel.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0
            private var downY = 0
            private var startX = 0
            private var startY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX.toInt()
                        downY = event.rawY.toInt()
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + event.rawX.toInt() - downX
                        params.y = startY + event.rawY.toInt() - downY
                        windowManager.updateViewLayout(panel, params)
                        return true
                    }
                }
                return false
            }
        })
        try {
            windowManager.addView(panel, params)
            overlay = panel
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("ChessLens Live")
            .setContentText("Захват экрана включён")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "ChessLens Live", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { overlay?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        overlay = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        worker?.quitSafely()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_DATA = "projection_data"
        const val ACTION_STOP = "com.chesslens.app.STOP_LIVE"
        private const val CHANNEL_ID = "chesslens_live"
        private const val NOTIFICATION_ID = 4201
    }
}
