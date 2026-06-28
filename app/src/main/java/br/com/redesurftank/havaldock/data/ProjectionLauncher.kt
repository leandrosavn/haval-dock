package br.com.redesurftank.havaldock.data

/**
 * Atalho de projeção (CarPlay / Android Auto).
 *
 * Detecta se há uma projeção rodando na central e se ela está em foco no Display 0, lendo a saída
 * de `am stack list` via [ShizukuShell] — mesma fonte que o Impulse usa (getStackList +
 * getTopPackageOnDisplay). Não depende do Impulse nem de broadcast.
 *
 * Toque no botão (no OverlayService): se a projeção está em foco -> volta pro app anterior (ou HOME);
 * senão -> guarda o app atual do Display 0 e abre a projeção.
 *
 * Packages/activities confirmados no fonte do Impulse (stable-64):
 *  - CarPlay:      com.ts.carplay.app / ...ui.display.view.CarPlayDisplayActivity
 *  - Android Auto: com.ts.androidauto.app / ...display.AapActivity
 *
 * Foco do 1º teste = CarPlay (Ai Box CarlinKit com fio). AA fica detectável pela mesma via.
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

    /** Lê uma vez o `am stack list` e resolve conexão + foco (CarPlay tem prioridade no teste). */
    fun probe(): State {
        val out = stackList() ?: return State(null, false)
        val connected = when {
            out.contains(CARPLAY_PKG) -> CARPLAY_PKG
            out.contains(AA_PKG) -> AA_PKG
            else -> null
        } ?: return State(null, false)
        return State(connected, topOnDisplay0(out) == connected)
    }

    /** Pacote no topo do Display 0 agora (p/ lembrar o "app anterior" antes de abrir a projeção). */
    fun currentTopOnDisplay0(): String? = stackList()?.let { topOnDisplay0(it) }

    private fun topOnDisplay0(out: String): String? {
        var cur: Int? = null
        for (line in out.lines()) {
            dispRe.find(line)?.let { cur = it.groupValues[1].toIntOrNull() }
            if (cur == 0) taskRe.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** Abre a projeção no Display 0 (traz pro foco). */
    fun open(pkg: String) {
        val act = if (pkg == AA_PKG) AA_ACTIVITY else CARPLAY_ACTIVITY
        ShizukuShell.exec("am", "start", "-n", "$pkg/$act", "--display", "0")
    }

    /** Volta pro app anterior (abre a activity de launcher do pacote). */
    fun openApp(pkg: String) {
        ShizukuShell.exec("monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")
    }

    /** Fallback: tela inicial da central. */
    fun goHome() {
        ShizukuShell.exec("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    }

    // ---- resize do CarPlay (encolher p/ a barra do dock) ----
    // Só encolhe DE FATO se o CarPlay patcheado (SurfaceView match_parent) estiver montado pelo
    // Impulse. Sem o patch, o vídeo é fixo (1896x700) e o resize da janela não muda nada.

    private const val DISPLAY_W = 1920
    private const val DISPLAY_H = 720
    private const val CARPLAY_SYSTEM_APK = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
    /** md5 do APK patcheado v13 (se bater, o patch está montado). */
    const val CARPLAY_PATCH_MD5 = "9d48c33f49dbeeb020c2fdc7e16bbc53"

    private val stackHeaderRe = Regex("""Stack id=(\d+).*displayId=(\d+)""")

    /** stackId da tarefa do CarPlay no Display 0 (ou null). */
    private fun carPlayStackIdOnDisplay0(): Int? {
        val out = stackList() ?: return null
        var stackId: Int? = null
        var disp: Int? = null
        for (line in out.lines()) {
            stackHeaderRe.find(line)?.let {
                stackId = it.groupValues[1].toIntOrNull(); disp = it.groupValues[2].toIntOrNull()
            }
            if (disp == 0 && line.contains(CARPLAY_PKG)) return stackId
        }
        return null
    }

    /** Encolhe a janela do CarPlay deixando [barPx] livres embaixo (p/ a barra). */
    fun shrinkCarPlay(barPx: Int) {
        val id = carPlayStackIdOnDisplay0() ?: return
        val h = (DISPLAY_H - barPx).coerceAtLeast(1)
        ShizukuShell.exec("am", "stack", "resize", id.toString(), "0", "0", DISPLAY_W.toString(), h.toString())
    }

    /** Restaura o CarPlay em tela cheia. */
    fun restoreCarPlay() {
        val id = carPlayStackIdOnDisplay0() ?: return
        ShizukuShell.exec("am", "stack", "resize", id.toString(), "0", "0", DISPLAY_W.toString(), DISPLAY_H.toString())
    }

    /** md5 do APK do CarPlay no sistema (p/ diagnóstico do patch). */
    fun carPlayApkMd5(): String? =
        ShizukuShell.exec("md5sum", CARPLAY_SYSTEM_APK)?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }

    /** true se o patch do CarPlay está montado (md5 bate com o patcheado). */
    fun isCarPlayPatchMounted(): Boolean = carPlayApkMd5() == CARPLAY_PATCH_MD5
}
