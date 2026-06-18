package br.com.redesurftank.havaldock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * TESTE (Etapa 1 da navegação): desenha um overlay rotulado em CADA display secundário, via
 * Presentation (mesma técnica do Impulse), pra descobrir qual display é o cluster e qual é o HUD,
 * e se o nosso overlay aparece e convive com o Impulse. Descartável depois.
 */
class DisplayProbeService : Service() {
    private val presentations = mutableListOf<Presentation>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        for (d in dm.displays) {
            Log.w(TAG, "Display encontrado: id=${d.displayId} name=${d.name}")
            if (d.displayId == Display.DEFAULT_DISPLAY) continue   // 0 = tela central, ignora
            runCatching {
                val p = ProbePresentation(this, d)
                p.show()
                presentations.add(p)
                Log.w(TAG, "Presentation MOSTRADA no display ${d.displayId} (${d.name})")
            }.onFailure { Log.e(TAG, "FALHA ao mostrar no display ${d.displayId}", it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presentations.forEach { runCatching { it.dismiss() } }
        presentations.clear()
    }

    /** Overlay translúcido com borda ciano + rótulo do display (pra ver onde aparece). */
    private class ProbePresentation(context: Context, display: Display) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context).apply { setBackgroundColor(0x2600E5FF) }  // ciano ~15%
            val border = View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT); setStroke(6, Color.parseColor("#FF2DE0F0"))
                }
            }
            root.addView(border, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val tv = TextView(context).apply {
                text = "HAVAL DOCK — TESTE\nDisplay ${display.displayId}\n${display.name}"
                setTextColor(Color.WHITE); textSize = 28f; gravity = Gravity.CENTER
                setShadowLayer(8f, 0f, 0f, Color.BLACK)
            }
            root.addView(tv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.CENTER })
            setContentView(root)
        }
    }

    private fun buildNotification(): Notification {
        val ch = "haval_dock_probe"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(ch) == null)
                nm.createNotificationChannel(NotificationChannel(ch, "Haval Dock — teste", NotificationManager.IMPORTANCE_MIN))
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, ch) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("Haval Dock — teste de displays")
            .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).build()
    }

    companion object {
        private const val TAG = "HavalDockProbe"
        private const val NOTIF_ID = 43
        fun start(c: Context) {
            val i = Intent(c, DisplayProbeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, DisplayProbeService::class.java)) }
    }
}
