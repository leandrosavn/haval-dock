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
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/** Resultado do teste, lido pela tela de Configurações (Diagnóstico). */
object ProbeResult {
    @Volatile var log: String = "(ligue o teste pra rodar)"
}

/**
 * TESTE/diagnóstico (Etapa 1 da navegação): enumera os displays, tenta desenhar um overlay
 * (Presentation, técnica do Impulse) em cada display secundário e REPORTA o que aconteceu
 * (na própria central, via [ProbeResult]) — pra sabermos se cluster/HUD aparecem e por quê.
 */
class DisplayProbeService : Service() {
    private val presentations = mutableListOf<Presentation>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { startForeground(NOTIF_ID, buildNotification()) }
        val sb = StringBuilder()
        try {
            val dm = getSystemService(DisplayManager::class.java)
            if (dm == null) { finish(sb.append("DisplayManager == null\n")); return }
            val displays = dm.displays ?: emptyArray()
            sb.append("Displays visíveis p/ o app: ${displays.size}\n")
            for (d in displays) {
                runCatching {
                    val m = DisplayMetrics()
                    @Suppress("DEPRECATION") d.getRealMetrics(m)
                    sb.append("• id=${d.displayId}  \"${d.name}\"  ${m.widthPixels}x${m.heightPixels}  flags=${d.flags}\n")
                }.onFailure { sb.append("• id=${d.displayId}: erro ao ler (${it.message})\n") }
            }
            sb.append("\n")
            for (d in displays) {
                if (d.displayId == Display.DEFAULT_DISPLAY) continue
                val r = tryShow(d)
                sb.append(if (r == null) "Display ${d.displayId}: overlay OK ✓\n"
                          else "Display ${d.displayId}: falhou — $r\n")
            }
            if (displays.size <= 1)
                sb.append("\n⚠️ Só a tela central foi exposta — cluster/HUD NÃO aparecem pra este app.\n")
        } catch (t: Throwable) {
            sb.append("\nERRO: ${t.javaClass.simpleName}: ${t.message}\n")
            t.stackTrace.take(5).forEach { sb.append("  at $it\n") }
        }
        finish(sb)
    }

    private fun finish(sb: StringBuilder) {
        ProbeResult.log = sb.toString()
        Log.w(TAG, "\n" + ProbeResult.log)
    }

    /** Tenta desenhar no display; null = sucesso. 1) contexto de display, 2) appCtx + overlay. */
    private fun tryShow(d: Display): String? {
        val dctx = runCatching { createDisplayContext(d) }.getOrNull() ?: applicationContext
        val e1 = show(dctx, d, null) ?: return null
        val t = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val e2 = show(applicationContext, d, t) ?: return null
        return "ctxDisplay($e1) / appCtx+overlay($e2)"
    }

    private fun show(ctx: Context, d: Display, type: Int?): String? = try {
        val p = ProbePresentation(ctx, d)
        if (type != null) p.window?.setType(type)
        p.show()
        presentations.add(p)
        null
    } catch (e: Throwable) {
        e.message ?: e.javaClass.simpleName
    }

    override fun onDestroy() {
        super.onDestroy()
        presentations.forEach { runCatching { it.dismiss() } }
        presentations.clear()
        ProbeResult.log = "(teste parado)"
    }

    private class ProbePresentation(context: Context, display: Display) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context).apply { setBackgroundColor(0x3300E5FF) }  // ciano ~20%
            root.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT); setStroke(8, Color.parseColor("#FF2DE0F0"))
                }
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            root.addView(TextView(context).apply {
                text = "HAVAL DOCK — TESTE\nDisplay ${display.displayId}\n${display.name}"
                setTextColor(Color.WHITE); textSize = 30f; gravity = Gravity.CENTER
                setShadowLayer(8f, 0f, 0f, Color.BLACK)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
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
