package br.com.redesurftank.havaldock.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Favoritos do rádio, espelhados do Haval Radio (dono da lista) via broadcast.
 *
 * O rádio publica ACTION_FAVORITES (CSVs de kHz por banda) sempre que a lista muda e ao subir;
 * o dock cacheia aqui (sobrevive a reboot) e pede a lista com [request] — broadcast EXPLÍCITO
 * pro receiver exported do rádio, que acorda o app dele se preciso (Android 9 só bloqueia
 * broadcast implícito pra manifest receiver; explícito passa, igual ao MediaCenterControl
 * do próprio rádio com o mediacenter).
 *
 * A sintonia é local: com o rádio TOCANDO (única situação em que os botões aparecem), escrever
 * cur_channel_info troca a estação com som — o foco de áudio já é do mediacenter. Retomar o
 * áudio do zero é que precisaria do broadcast de tecla do volante, o que não é o caso aqui.
 */
object RadioFavorites {
    const val ACTION_FAVORITES = "br.com.redesurftank.havalradio.FAVORITES"
    const val EXTRA_FM = "fm"
    const val EXTRA_AM = "am"

    private const val RADIO_PKG = "br.com.redesurftank.havalradio"
    private const val RADIO_REQUEST_RECEIVER = "br.com.redesurftank.havalradio.data.FavoritesRequestReceiver"

    private const val PREFS = "radio_favorites"

    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    private fun prefs() = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Guarda os CSVs publicados pelo rádio (null = extra ausente, mantém o que tinha). */
    fun update(fm: String?, am: String?) {
        val e = prefs().edit()
        if (fm != null) e.putString(EXTRA_FM, fm)
        if (am != null) e.putString(EXTRA_AM, am)
        e.apply()
    }

    /** Lista de kHz da banda (0=FM, 1=AM), na ordem do usuário no Haval Radio. */
    fun list(bandCode: Int): List<Int> =
        prefs().getString(if (bandCode == 1) EXTRA_AM else EXTRA_FM, "")
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?: emptyList()

    /** Pede a lista ao Haval Radio (explícito; acorda o processo dele se estiver morto). */
    fun request() {
        runCatching {
            appCtx.sendBroadcast(
                Intent("br.com.redesurftank.havalradio.REQUEST_FAVORITES")
                    .setComponent(ComponentName(RADIO_PKG, RADIO_REQUEST_RECEIVER))
            )
        }
    }

    // ---- canal atual (sys.radio.cur_channel_info = {freqKHz,banda,play,estéreo}) ----

    data class Channel(val freqKHz: Int, val band: Int, val playing: Boolean)

    fun parseChannel(raw: String?): Channel? {
        val n = raw?.trim()?.trim('{', '}', ' ')
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
        if (n.size < 3) return null
        return Channel(n[0], n[1], n[2] == 1)
    }

    /** Rótulo da estação: FM "94.7 MHz", AM "540 kHz". */
    fun label(freqKHz: Int, band: Int): String =
        if (band == 1) "$freqKHz kHz"
        else String.format(Locale.US, "%.1f MHz", freqKHz / 1000.0)
}
