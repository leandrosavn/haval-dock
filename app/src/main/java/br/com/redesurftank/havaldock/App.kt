package br.com.redesurftank.havaldock

import android.app.Application
import br.com.redesurftank.havaldock.data.SettingsStore
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Libera o acesso a APIs ocultas (android.os.ServiceManager#getService) usado no bind do veículo.
        runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        SettingsStore.init(this)
    }
}
