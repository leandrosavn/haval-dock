package br.com.redesurftank.havaldock.data

import androidx.annotation.DrawableRes
import br.com.redesurftank.havaldock.R
import java.util.Locale

/** Chaves do IntelligentVehicleControlService usadas pela barra (do CarConstants do Impulse). */
object DockKeys {
    const val DRIVER_TEMP = "car.hvac.driver_temperature"
    const val PASS_TEMP = "car.hvac.pass_temperature"
    const val FRONT_TEMP_RANGE = "car.hvac.front_temperature_range"
    const val FAN_SPEED = "car.hvac.fan_speed"
    const val FAN_RANGE = "car.hvac.fan_speed_range"
    const val DRIVER_SEAT_VENT = "car.comfort_setting.driver_seat_ventilation_level"
    const val PASS_SEAT_VENT = "car.comfort_setting.passenger_seat_ventilation_level"
    const val SEAT_VENT_MAX = "car.comfort_setting.seat_ventilation_max_level"
    const val CYCLE_MODE = "car.hvac.cycle_mode"
    const val DRIVE_MODE = "car.drive_setting.drive_mode"
    const val STEER_MODE = "car.drive_setting.steering_wheel_assist_mode"
    const val REGEN_LEVEL = "car.ev_setting.energy_recovery_level"
    const val MEDIA_VOLUME = "sys.settings.audio.media_volume"
    const val MEDIA_VOLUME_RANGE = "sys.settings.audio.media_volume_range"
}

/** Extrai o maior número de uma string ("7", "0,7", "{0,7}"...) — usado para ler ranges/max. */
private fun parseMax(s: String?): Double? {
    if (s == null) return null
    return Regex("-?\\d+(\\.\\d+)?").findAll(s).mapNotNull { it.value.toDoubleOrNull() }.maxOrNull()
}

sealed class Control(
    val id: String,
    @DrawableRes val icon: Int,
    val label: String,
    val group: Int,
) {
    /** Texto a exibir no tile (lê o valor atual do veículo). */
    abstract fun display(): String
}

/** Controle de incremento/decremento: escreve o VALOR direto (igual o Impulse faz com fan/temp). */
class Stepper(
    id: String, @DrawableRes icon: Int, label: String, group: Int,
    val key: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val decimals: Int,
    val suffix: String = "",
    val rangeKey: String? = null,
) : Control(id, icon, label, group) {

    private fun fmt(v: Double): String =
        if (decimals == 1) String.format(Locale.US, "%.1f", v) else v.toInt().toString()

    override fun display(): String {
        val v = VehicleClient.getData(key)?.trim()?.toDoubleOrNull() ?: return "—"
        return fmt(v) + suffix
    }

    fun nudge(dir: Int) {
        val cur = VehicleClient.getData(key)?.trim()?.toDoubleOrNull() ?: return
        val hi = rangeKey?.let { parseMax(VehicleClient.getData(it)) } ?: max
        val nv = (cur + dir * step).coerceIn(min, hi)
        VehicleClient.set(key, fmt(nv))
    }
}

/** Atalho que cicla entre valores (drive/steer/regen): escreve o código int do próximo. */
class Cycle(
    id: String, @DrawableRes icon: Int, label: String, group: Int,
    val key: String,
    val order: List<Int>,
    val labels: Map<Int, String>,
) : Control(id, icon, label, group) {

    private fun current(): Int? = VehicleClient.getData(key)?.trim()?.toIntOrNull()

    override fun display(): String = labels[current()] ?: "—"

    fun next() {
        val idx = order.indexOf(current())
        val nextVal = order[(idx + 1).mod(order.size)]
        VehicleClient.set(key, nextVal.toString())
    }

    /** Posição atual no ciclo (para os pontinhos), ou -1. */
    fun index(): Int = order.indexOf(current())
    val size: Int get() = order.size
}

/** Liga/desliga (recirculador): cycle_mode 1=recirc, 0=externo. */
class Toggle(
    id: String, @DrawableRes icon: Int, label: String, group: Int,
    val key: String,
    val onValue: String,
    val offValue: String,
    val onLabel: String,
    val offLabel: String,
) : Control(id, icon, label, group) {

    fun isOn(): Boolean = VehicleClient.getData(key)?.trim() == onValue

    override fun display(): String = if (isOn()) onLabel else offLabel

    fun flip() = VehicleClient.set(key, if (isOn()) offValue else onValue)
}

/** A barra: 10 controles, ESQUERDA -> DIREITA, agrupados por divisória (group). */
object DockControls {
    val ALL: List<Control> = listOf(
        Stepper("tempD", R.drawable.ic_thermo, "Temp. motorista", 0,
            DockKeys.DRIVER_TEMP, 16.0, 32.0, 0.5, 1, "°", DockKeys.FRONT_TEMP_RANGE),
        Stepper("ventD", R.drawable.ic_seat, "Ventil. motorista", 0,
            DockKeys.DRIVER_SEAT_VENT, 0.0, 3.0, 1.0, 0, "", DockKeys.SEAT_VENT_MAX),
        Stepper("fan", R.drawable.ic_fan, "Veloc. ar-cond.", 1,
            DockKeys.FAN_SPEED, 0.0, 7.0, 1.0, 0, "", DockKeys.FAN_RANGE),
        Toggle("recirc", R.drawable.ic_recirc, "Recirculador", 1,
            DockKeys.CYCLE_MODE, "1", "0", "Recirc.", "Externo"),
        Cycle("drive", R.drawable.ic_car, "Modo condução", 2,
            DockKeys.DRIVE_MODE, listOf(0, 2, 1),
            mapOf(0 to "Normal", 1 to "Sport", 2 to "Eco", 3 to "Neve", 4 to "Areia", 5 to "Lama", 11 to "AWD")),
        Cycle("steer", R.drawable.ic_steer, "Modo direção", 2,
            DockKeys.STEER_MODE, listOf(2, 0, 1),
            mapOf(2 to "Conforto", 0 to "Normal", 1 to "Esportiva")),
        Cycle("regen", R.drawable.ic_regen, "Regeneração", 2,
            DockKeys.REGEN_LEVEL, listOf(2, 0, 1),
            mapOf(2 to "Baixo", 0 to "Normal", 1 to "Alto")),
        Stepper("tempP", R.drawable.ic_thermo, "Temp. passageiro", 3,
            DockKeys.PASS_TEMP, 16.0, 32.0, 0.5, 1, "°", DockKeys.FRONT_TEMP_RANGE),
        Stepper("ventP", R.drawable.ic_seat, "Ventil. passageiro", 3,
            DockKeys.PASS_SEAT_VENT, 0.0, 3.0, 1.0, 0, "", DockKeys.SEAT_VENT_MAX),
        Stepper("vol", R.drawable.ic_volume, "Volume rádio", 4,
            DockKeys.MEDIA_VOLUME, 0.0, 30.0, 1.0, 0, "", DockKeys.MEDIA_VOLUME_RANGE),
    )

    /** Chaves monitoradas pelo listener (para atualizar a barra ao vivo). */
    val MONITORED: List<String> = ALL.map {
        when (it) {
            is Stepper -> it.key
            is Cycle -> it.key
            is Toggle -> it.key
        }
    }
}
