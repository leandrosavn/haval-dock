package br.com.redesurftank.havaldock.data

/**
 * Teste de viabilidade: reservar uma faixa inferior no Display 0 via `wm overscan`, pra ver se o
 * vídeo do CarPlay/Android Auto encolhe (sobe) ou continua renderizando a altura cheia.
 *
 * Roda o comando direto pelo Shizuku (uid shell) — NÃO depende da barra do Impulse nem do broadcast.
 * `wm overscan` é uma propriedade de sistema do display padrão (Display 0 = central principal);
 * persiste até `reset` ou reboot.
 *
 * Resultado esperado de um dos dois:
 *  - vídeo do CarPlay/AA sobe deixando a faixa livre → dá pra fazer no dock standalone.
 *  - janela "encolhe" mas o vídeo segue 720 (faixa preta / corte) → confirma o bloqueador de
 *    SurfaceView/HWC (precisa patchear o APK da projeção, trabalho no Impulse).
 */
object OverscanTest {
    /** Reserva [px] pixels embaixo no Display 0. Devolve o stdout/stderr do comando (ou null). */
    fun apply(px: Int): String? = ShizukuShell.exec("wm", "overscan", "0,0,0,$px")

    /** Remove o overscan (volta ao normal). */
    fun reset(): String? = ShizukuShell.exec("wm", "overscan", "reset")

    /** Métricas do display padrão, p/ contexto no resultado (ex.: "Physical size: 1920x720"). */
    fun size(): String? = ShizukuShell.exec("wm", "size")
}
