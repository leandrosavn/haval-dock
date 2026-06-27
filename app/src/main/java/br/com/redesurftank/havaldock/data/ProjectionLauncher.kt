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
}
