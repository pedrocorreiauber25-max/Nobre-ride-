package pt.nobreride.copilot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import pt.nobreride.copilot.radar.ScreenRadarService

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 7101
    }

    private lateinit var status: TextView
    private lateinit var latest: TextView
    private lateinit var offerSummary: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshText = object : Runnable {
        override fun run() {
            refreshLatestText()
            mainHandler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 24, 28, 28)
            setBackgroundColor(Color.rgb(10, 10, 12))
        }

        val brandArtwork = ImageView(this).apply {
            setImageResource(R.drawable.nobre_ride_brand)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Símbolo dourado da marca Nobre Ride Copilot"
        }
        root.addView(brandArtwork, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 132
        ))

        val brand = TextView(this).apply {
            text = "NOBRE RIDE"
            textSize = 28f
            setTextColor(Color.rgb(214, 176, 92))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "COPILOT  •  RADAR DE VIAGENS"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 24)
        }
        status = TextView(this).apply {
            text = "RADAR DESLIGADO"
            textSize = 15f
            setTextColor(Color.rgb(235, 190, 90))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 22)
        }
        val start = Button(this).apply {
            text = "AUTORIZAR E INICIAR RADAR"
            setOnClickListener { requestScreenCapture() }
        }
        val stop = Button(this).apply {
            text = "PARAR RADAR"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenRadarService::class.java))
                status.text = "RADAR DESLIGADO"
            }
        }
        latest = TextView(this).apply {
            text = "O texto detetado no ecrã aparecerá aqui."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 0)
        }
        offerSummary = TextView(this).apply {
            text = "À espera de uma oferta com preço, distância e duração."
            textSize = 15f
            setTextColor(Color.rgb(214, 176, 92))
            setPadding(0, 16, 0, 0)
        }
        val note = TextView(this).apply {
            text = "A captura só começa após autorização explícita do Android. Nesta fase, o radar lê texto do ecrã; não aceita nem rejeita viagens."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 24, 0, 0)
        }

        root.addView(brand)
        root.addView(subtitle)
        root.addView(status)
        root.addView(start)
        root.addView(stop)
        root.addView(latest)
        root.addView(offerSummary)
        root.addView(note)
        setContentView(root)
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
    }

    @Deprecated("Deprecated by Android, retained for broad platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREEN_CAPTURE) return

        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenRadarService::class.java).apply {
                putExtra(ScreenRadarService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRadarService.EXTRA_RESULT_DATA, data)
            }
            try {
                startForegroundService(serviceIntent)
                status.text = "A INICIAR RADAR…"
            } catch (error: Exception) {
                status.text = "Não foi possível iniciar: ${error.localizedMessage}"
            }
        } else {
            status.text = "AUTORIZAÇÃO CANCELADA"
        }
    }

    private fun refreshLatestText() {
        val prefs = getSharedPreferences("copilot_radar", MODE_PRIVATE)
        val detected = prefs.getString("latest_ocr", "Ainda não foi detetado texto.")
            ?: "Ainda não foi detetado texto."
        latest.text = detected
        offerSummary.text = prefs.getString("latest_offer", "À espera de uma oferta com preço, distância e duração.")
            ?: "À espera de uma oferta com preço, distância e duração."
        val timestamp = prefs.getLong("latest_ocr_at", 0L)
        if (timestamp > 0L && System.currentTimeMillis() - timestamp < 5000L) {
            status.text = "RADAR ATIVO • texto detetado"
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(refreshText)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(refreshText)
        super.onPause()
    }
}
