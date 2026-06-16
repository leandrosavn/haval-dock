package br.com.redesurftank.havaldock.data

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.beantechs.intelligentvehiclecontrol.IIntelligentVehicleControlService
import com.beantechs.intelligentvehiclecontrol.sdk.IListener
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/**
 * Conecta-se ao IntelligentVehicleControlService (SDK Beantechs) via Shizuku, obtendo o binder
 * de sistema por reflexão em android.os.ServiceManager#getService (igual ao Impulse e ao Haval Radio).
 *
 * Pré-requisitos no carro: Shizuku ativo + permissão concedida. Escrita = request("cmd.common.request.set",
 * key, value); leitura = fetchData(key) (mesmo mecanismo que o Impulse usa para HVAC/condução).
 */
object VehicleClient {
    private const val TAG = "HavalDock"
    private const val SERVICE_NAME = "com.beantechs.intelligentvehiclecontrol"
    private const val PKG = "br.com.redesurftank.havaldock"
    private const val ACTION_SET = "cmd.common.request.set"

    @Volatile private var service: IIntelligentVehicleControlService? = null

    private val getServiceMethod by lazy {
        runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
        }.onFailure { Log.w(TAG, "Reflexão de ServiceManager falhou", it) }.getOrNull()
    }

    fun isShizukuReady(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    @Synchronized
    fun ensureConnected(): Boolean {
        if (service != null) return true
        if (!isShizukuReady()) return false
        return runCatching {
            val raw = getServiceMethod?.invoke(null, SERVICE_NAME) as? IBinder
                ?: return false
            service = IIntelligentVehicleControlService.Stub.asInterface(ShizukuBinderWrapper(raw))
            service != null
        }.onFailure { Log.e(TAG, "Falha ao conectar no serviço do veículo", it) }.getOrDefault(false)
    }

    fun getData(key: String): String? = runCatching {
        ensureConnected(); service?.fetchData(key)
    }.onFailure { Log.e(TAG, "getData $key", it) }.getOrNull()

    /** Escreve uma propriedade (ação de set padrão do SDK). */
    fun set(key: String, value: String) {
        runCatching {
            if (ensureConnected()) service?.request(ACTION_SET, key, value)
        }.onFailure { Log.e(TAG, "set $key=$value", it) }
    }

    fun registerListener(keys: List<String>, listener: IListener) {
        runCatching {
            if (!ensureConnected()) return
            service?.addListenerKey(PKG, keys.toTypedArray())
            service?.registerDataChangedListener(PKG, listener)
        }.onFailure { Log.e(TAG, "registerListener", it) }
    }

    fun unregisterListener(listener: IListener) {
        runCatching { service?.unRegisterDataChangedListener(PKG, listener) }
            .onFailure { Log.e(TAG, "unregisterListener", it) }
    }
}
