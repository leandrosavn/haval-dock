package br.com.redesurftank.havaldock.data

/**
 * Diagnóstico do patch de projeção do CarPlay.
 *
 * (O botão de atalho e o resize ao vivo foram removidos do dock — o resize dava tela branca
 *  porque encolher a janela com a sessão ativa recria a Surface no meio do callback; o fix
 *  correto é no patch do CarPlay, que dimensiona a Surface no init, não no dock.)
 *
 * Mantido só o check read-only: confirmar, direto no app (via Shizuku), se o APK patcheado do
 * CarPlay está montado pelo Impulse — pré-requisito do resize.
 */
object ProjectionLauncher {
    private const val CARPLAY_SYSTEM_APK = "/system/app/TsCarPlayApp/TsCarPlayApp.apk"
    /** md5 do APK patcheado v13 (se bater, o patch está montado). */
    const val CARPLAY_PATCH_MD5 = "9d48c33f49dbeeb020c2fdc7e16bbc53"

    /** md5 do APK do CarPlay no sistema (p/ diagnóstico do patch). */
    fun carPlayApkMd5(): String? =
        ShizukuShell.exec("md5sum", CARPLAY_SYSTEM_APK)?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() }

    /** true se o patch do CarPlay está montado (md5 bate com o patcheado). */
    fun isCarPlayPatchMounted(): Boolean = carPlayApkMd5() == CARPLAY_PATCH_MD5
}
