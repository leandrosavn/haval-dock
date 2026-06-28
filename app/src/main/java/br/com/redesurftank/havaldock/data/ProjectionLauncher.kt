package br.com.redesurftank.havaldock.data

/**
 * Atalho de projeção (CarPlay / Android Auto).
 *
 * Detecta, lendo `am stack list` via [ShizukuShell]:
 *  - se há um dispositivo de projeção CONECTADO (tarefa do app existe);
 *  - se a projeção está EM FOCO no Display 0 (topo).
 *
 * Botão (no OverlayService): aparece quando há projeção conectada.
 *  - fora da projeção -> mostra o ícone da marca; toque ABRE a projeção.
 *  - na projeção       -> mostra o ícone do carro; toque volta pra TELA DA CENTRAL (HOME).
 *
 * Packages/activities (confirmados no Impulse stable-64):
 *  - CarPlay:      com.ts.carplay.app / ...ui.display.view.CarPlayDisplayActivity
 *  - Android Auto: com.ts.androidauto.app / ...display.AapActivity
 */
object ProjectionLauncher {
    const val CARPLAY_PKG = "com.ts.carplay.app"
    private const val CARPLAY_ACTIVITY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    const val AA_PKG = "com.ts.androidauto.app"
    private const val AA_ACTIVITY = "com.ts.androidauto.app.display.AapActivity"

    /** connected = pacote da projeção com tarefa ativa (ou null); foreground = está no topo do Display 0. */
    data class State(val connected: String?, val foreground: Boolean)

    private val taskRe = Regex("""taskId=\d+:\s*([a-zA-Z0-9._]+)/""")
    private val dispRe = Regex("""displayId=(\d+)""")

    private fun stackList(): String? = ShizukuShell.exec("am", "stack", "list")

    /** Lê o `am stack list` e resolve conexão + foco (CarPlay tem prioridade). */
    fun probe(): State {
        val out = stackList() ?: return State(null, false)
        val connected = when {
            out.contains(CARPLAY_PKG) -> CARPLAY_PKG
            out.contains(AA_PKG) -> AA_PKG
            else -> null
        } ?: return State(null, false)
        return State(connected, topOnDisplay0(out) == connected)
    }

    private fun topOnDisplay0(out: String): String? {
        var cur: Int? = null
        for (line in out.lines()) {
            dispRe.find(line)?.let { cur = it.groupValues[1].toIntOrNull() }
            if (cur == 0) taskRe.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /**
     * Traz a projeção pro foco no Display 0. Usa o flag de cold-start aceito pelo contrato do
     * Impulse (0x14000000) e NÃO usa `--display 0` (que foi observado caindo numa instância
     * stale no cluster).
     */
    fun openProjection(pkg: String) {
        val act = if (pkg == AA_PKG) AA_ACTIVITY else CARPLAY_ACTIVITY
        ShizukuShell.exec("am", "start", "-f", "0x14000000", "-n", "$pkg/$act")
    }

    /** Volta pra tela da central (launcher/home). */
    fun goHome() {
        ShizukuShell.exec("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    }

    // ---- diagnóstico do patch do CarPlay (read-only) ----
    private const val CARPLAY_SYSTEM_APK = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
    /** md5 do APK patcheado v13 (se bater, o patch está montado). */
    const val CARPLAY_PATCH_MD5 = "9d48c33f49dbeeb020c2fdc7e16bbc53"

    fun carPlayApkMd5(): String? =
        ShizukuShell.exec("md5sum", CARPLAY_SYSTEM_APK)?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }

    fun isCarPlayPatchMounted(): Boolean = carPlayApkMd5() == CARPLAY_PATCH_MD5
}
