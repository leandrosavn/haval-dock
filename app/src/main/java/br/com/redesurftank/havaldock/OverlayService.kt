package br.com.redesurftank.havaldock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redesurftank.havaldock.data.Control
import br.com.redesurftank.havaldock.data.Cycle
import br.com.redesurftank.havaldock.data.DockControls
import br.com.redesurftank.havaldock.data.SettingsStore
import br.com.redesurftank.havaldock.data.Stepper
import br.com.redesurftank.havaldock.data.Toggle
import br.com.redesurftank.havaldock.data.VehicleClient
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import java.util.concurrent.Executors

/**
 * Serviço (foreground) que desenha a toolbar inferior como OVERLAY (TYPE_APPLICATION_OVERLAY),
 * sempre por cima do mediacenter/CarPlay, SÓ na faixa de baixo. Lê/escreve as funções do veículo
 * pelo [VehicleClient] e atualiza os tiles ao vivo via listener do serviço Beantechs.
 */
class OverlayService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var root: TouchFrame
    private lateinit var bar: LinearLayout
    private lateinit var handle: TextView

    private val valueViews = HashMap<String, TextView>()
    private var hidden = false

    private val barHeightPx by lazy { dp(90) }
    private val handleHeightPx by lazy { dp(22) }

    // cores (mesmo tema do protótipo)
    private val cAccent = Color.parseColor("#19E3B1")
    private val cAccentSoft = Color.parseColor("#5FF0CF")
    private val cCard = Color.parseColor("#121722")
    private val cLine = Color.parseColor("#23FFFFFF")
    private val cTxt = Color.parseColor("#EEF2F8")
    private val cMuted = Color.parseColor("#9099A8")
    private val cBarBg = Color.parseColor("#F20A0E14")
    private val cOnAccent = Color.parseColor("#04140F")

    private val hideRunnable = Runnable { hideBar() }

    private val listener = object : IListener.Stub() {
        override fun onDataChanged(key: String?, value: String?) {
            main.post { refreshAll() }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SettingsStore.KEY_MODE || key == SettingsStore.KEY_SECS) applyVisibility()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
        SettingsStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        io.execute { runCatching { VehicleClient.registerListener(DockControls.MONITORED, listener) } }
        // leituras iniciais (Shizuku pode demorar a ficar pronto)
        refreshAll()
        main.postDelayed({ refreshAll() }, 1500)
        main.postDelayed({ refreshAll() }, 4000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyVisibility()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(hideRunnable)
        runCatching { SettingsStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener) }
        io.execute { runCatching { VehicleClient.unregisterListener(listener) } }
        runCatching { wm.removeView(root) }
    }

    // ---- construção do overlay ----

    private fun buildOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        root = TouchFrame(this) { onUserActivity() }

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(cBarBg)
            setPadding(dp(18), dp(8), dp(18), dp(9))
        }
        buildTiles(bar)
        root.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        handle = TextView(this).apply {
            text = "▴ Haval Dock"
            setTextColor(cAccentSoft)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(5), dp(22), dp(6))
            background = pill(cBarBg, dp(12), topOnly = true)
            visibility = View.GONE
            setOnClickListener { showBar() }
        }
        root.addView(handle, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        wm.addView(root, params)
    }

    private fun buildTiles(container: LinearLayout) {
        var prevGroup = -1
        for (c in DockControls.ALL) {
            if (prevGroup != -1 && c.group != prevGroup) container.addView(divider())
            container.addView(tile(c), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            prevGroup = c.group
        }
    }

    private fun divider(): View = View(this).apply {
        background = GradientDrawable().apply { setColor(cLine) }
        val lp = LinearLayout.LayoutParams(dp(1), dp(58))
        lp.gravity = Gravity.CENTER_VERTICAL
        lp.marginStart = dp(7); lp.marginEnd = dp(7)
        layoutParams = lp
    }

    private fun tile(c: Control): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(c.icon)
            setColorFilter(cAccentSoft)
        }
        col.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { bottomMargin = dp(7) })

        when (c) {
            is Stepper -> {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                val tv = valueText()
                row.addView(stepButton("−") { act(c) { c.nudge(-1) } })
                row.addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8); marginEnd = dp(8) })
                row.addView(stepButton("+") { act(c) { c.nudge(1) } })
                valueViews[c.id] = tv
                col.addView(row)
            }
            is Cycle -> {
                val tv = modeText()
                valueViews[c.id] = tv
                col.addView(tv)
                col.setOnClickListener { act(c) { c.next() } }
                col.isClickable = true
            }
            is Toggle -> {
                val tv = pillText()
                valueViews[c.id] = tv
                col.addView(tv)
                col.setOnClickListener { act(c) { c.flip() } }
                col.isClickable = true
            }
        }
        return col
    }

    private fun valueText() = TextView(this).apply {
        setTextColor(cTxt); textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER; text = "—"
    }

    private fun modeText() = TextView(this).apply {
        setTextColor(cAccentSoft); textSize = 14f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER; text = "—"
    }

    private fun pillText() = TextView(this).apply {
        setTextColor(cMuted); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER; setPadding(dp(14), dp(6), dp(14), dp(6))
        background = pill(cCard, dp(11)); text = "—"
    }

    private fun stepButton(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(cTxt); textSize = 18f; gravity = Gravity.CENTER
        background = pill(cCard, dp(9))
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        isClickable = true
        setOnClickListener { onClick() }
    }

    // ---- ações / refresh (IPC sempre fora da main thread) ----

    private fun act(c: Control, action: () -> Unit) {
        onUserActivity()
        io.execute {
            runCatching { action() }
            val text = c.display()
            val on = (c as? Toggle)?.isOn()
            main.post { applyTile(c, text, on) }
        }
    }

    private fun refreshAll() {
        io.execute {
            val snap = DockControls.ALL.map { c ->
                Triple(c, c.display(), (c as? Toggle)?.isOn())
            }
            main.post { snap.forEach { (c, text, on) -> applyTile(c, text, on) } }
        }
    }

    private fun applyTile(c: Control, text: String, on: Boolean?) {
        val tv = valueViews[c.id] ?: return
        tv.text = text
        if (c is Toggle) {
            if (on == true) {
                tv.background = pill(cAccent, dp(11)); tv.setTextColor(cOnAccent)
            } else {
                tv.background = pill(cCard, dp(11)); tv.setTextColor(cMuted)
            }
        }
    }

    // ---- visibilidade / auto-ocultar ----

    private fun onUserActivity() {
        if (hidden) showBar() else armTimer()
    }

    private fun applyVisibility() {
        showBar()
    }

    private fun armTimer() {
        main.removeCallbacks(hideRunnable)
        if (SettingsStore.mode(this) == SettingsStore.MODE_AUTO) {
            main.postDelayed(hideRunnable, SettingsStore.secs(this) * 1000L)
        }
    }

    private fun showBar() {
        main.removeCallbacks(hideRunnable)
        if (hidden) {
            hidden = false
            bar.visibility = View.VISIBLE
            handle.visibility = View.GONE
            params.height = barHeightPx
            runCatching { wm.updateViewLayout(root, params) }
        }
        armTimer()
    }

    private fun hideBar() {
        if (SettingsStore.mode(this) != SettingsStore.MODE_AUTO) return
        hidden = true
        bar.visibility = View.GONE
        handle.visibility = View.VISIBLE
        params.height = handleHeightPx
        runCatching { wm.updateViewLayout(root, params) }
    }

    // ---- utilidades ----

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(fill: Int, radius: Int, topOnly: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            if (topOnly) cornerRadii = floatArrayOf(
                radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(), 0f, 0f, 0f, 0f)
            else cornerRadius = radius.toFloat()
            setStroke(dp(1), cLine)
        }

    private fun buildNotification(): Notification {
        val channelId = "haval_dock_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Haval Dock", NotificationManager.IMPORTANCE_MIN))
            }
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId) else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("Haval Dock")
            .setContentText("Barra inferior ativa")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    /** FrameLayout que avisa qualquer toque (para resetar o timer de inatividade). */
    private class TouchFrame(context: Context, val onTouch: () -> Unit) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
            if (ev?.actionMasked == MotionEvent.ACTION_DOWN) onTouch()
            return super.dispatchTouchEvent(ev)
        }
    }

    companion object {
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
