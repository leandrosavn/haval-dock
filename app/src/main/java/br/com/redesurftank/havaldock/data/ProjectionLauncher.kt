package br.com.redesurftank.havaldock.data

/**
 * Atalho de projeção (CarPlay / Android Auto).
 *
 * Detecção robusta lendo `am stack list` via [ShizukuShell]:
 *  - [foregroundProjection]: QUAL projeção está no topo do Display 0 (foco), por like-matching
 *    (carplay/zlink -> CarPlay; androidauto/gearhead -> AA). NÃO usa mera presença do pacote,
 *    porque o app do Android Auto fica sempre rodando (tarefa "fantasma") e dava falso-positivo.
 *  - [carPlayConnected]: CarPlay conectado mas em background (processo rodando) — p/ o botão
 *    aparecer mesmo fora da projeção.
 *
 * Botão (OverlayService): fora da projeção mostra o ícone da marca e ABRE; na projeção mostra o
 * carro e volta pra TELA DA CENTRAL (HOME).
 */
object ProjectionLauncher {
    const val CARPLAY_PKG = "com.ts.carplay.app"
    private const val CARPLAY_ACTIVITY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    const val AA_PKG = "com.ts.androidauto.app"
    private const val AA_ACTIVITY = "com.ts.androidauto.app.display.AapActivity"

    private val taskRe = Regex("""taskId=\d+:\s*([a-zA-Z0-9._]+)/""")
    private val dispRe = Regex("""displayId=(\d+)""")

    private fun stackList(): String? = ShizukuShell.exec("am", "stack", "list")

    /** Pacote no topo do Display 0 (string crua) ou null. */
    private fun topOnDisplay0(out: String): String? {
        var cur: Int? = null
        for (line in out.lines()) {
            dispRe.find(line)?.let { cur = it.groupValues[1].toIntOrNull() }
            if (cur == 0) taskRe.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** Classifica um pacote como projeção (canônico) por like-matching, ou null. */
    private fun classify(pkg: String?): String? {
        val t = (pkg ?: return null).lowercase()
        return when {
            t.contains("carplay") || t.contains("zlink") -> CARPLAY_PKG
            t.contains("androidauto") || t.contains("gearhead") -> AA_PKG
            else -> null
        }
    }

    /** Projeção EM FOCO no Display 0 agora (pacote canônico) ou null. */
    fun foregroundProjection(): String? {
        val out = stackList() ?: return null
        return classify(topOnDisplay0(out))
    }

    /** CarPlay conectado (processo rodando), mesmo em background. */
    fun carPlayConnected(): Boolean = !ShizukuShell.exec("pidof", CARPLAY_PKG).isNullOrBlank()

    /**
     * Traz a projeção pro foco no Display 0. Flag de cold-start aceito pelo contrato do Impulse
     * (0x14000000); NÃO usa `--display 0` (que caía em instância stale no cluster).
     */
    fun openProjection(pkg: String) {
        val act = if (pkg == AA_PKG) AA_ACTIVITY else CARPLAY_ACTIVITY
        ShizukuShell.exec("am", "start", "-f", "0x14000000", "-n", "$pkg/$act")
    }

    /** Volta pra tela da central (launcher/home). */
    fun goHome() {
        ShizukuShell.exec("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    }

    /** Linha de diagnóstico de detecção (p/ ver no app, sem PC). */
    fun detectDebug(): String {
        val out = stackList() ?: return "am stack list: sem leitura (Shizuku?)"
        val top = topOnDisplay0(out) ?: "(nenhum)"
        val cpStack = if (out.lowercase().contains("carplay")) "sim" else "não"
        val aaStack = if (out.lowercase().contains("androidauto")) "sim" else "não"
        val cpPid = if (carPlayConnected()) "sim" else "não"
        return "topo D0: $top\ncarplay no stack: $cpStack | androidauto: $aaStack | pidof carplay: $cpPid"
    }

    // ---- diagnóstico do patch do CarPlay (read-only) ----
    private const val CARPLAY_SYSTEM_APK = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
    /** md5 do APK patcheado v13 (se bater, o patch está montado). */
    const val CARPLAY_PATCH_MD5 = "9d48c33f49dbeeb020c2fdc7e16bbc53"

    fun carPlayApkMd5(): String? =
        ShizukuShell.exec("md5sum", CARPLAY_SYSTEM_APK)?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }

    fun isCarPlayPatchMounted(): Boolean = carPlayApkMd5() == CARPLAY_PATCH_MD5
}
