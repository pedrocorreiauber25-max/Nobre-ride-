package pt.nobreride.copilot.radar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
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
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRadarService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "nobre_radar"
        private const val NOTIFICATION_ID = 4101
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val processing = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startCapture(resultCode, data)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Radar Nobre Ride", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Nobre Ride Copilot")
            .setContentText("Radar de ecrã ativo — para terminar, abre a aplicação e toca em Parar")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        worker = HandlerThread("NobreRideOCR").also { it.start() }
        handler = Handler(worker!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            if (now - lastFrameAt < 1400 || !processing.compareAndSet(false, true)) {
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            lastFrameAt = now
            val screenImage = reader.acquireLatestImage()
            if (screenImage == null) {
                processing.set(false)
                return@setOnImageAvailableListener
            }

            var paddedBitmap: Bitmap? = null
            var croppedBitmap: Bitmap? = null
            try {
                val plane = screenImage.planes[0]
                val pixelStride = plane.pixelStride
                val rowPadding = plane.rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride
                val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                paddedBitmap = padded
                padded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                croppedBitmap = cropped

                recognizer.process(InputImage.fromBitmap(cropped, 0))
                    .addOnSuccessListener { result ->
                        val detected = result.text.trim()
                        if (detected.isNotEmpty()) {
                            getSharedPreferences("copilot_radar", MODE_PRIVATE)
                                .edit()
                                .putString("latest_ocr", detected)
                                .putString("latest_offer", OfferDataParser.parse(detected).summary())
                                .putLong("latest_ocr_at", now)
                                .apply()
                        }
                    }
                    .addOnCompleteListener {
                        croppedBitmap?.recycle()
                        paddedBitmap?.recycle()
                        processing.set(false)
                    }
            } catch (_: Exception) {
                croppedBitmap?.recycle()
                paddedBitmap?.recycle()
                processing.set(false)
            } finally {
                screenImage.close()
            }
        }, handler)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "NobreRideRadar",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        worker?.quitSafely()
        recognizer.close()
        super.onDestroy()
    }
}
