package br.com.redesurftank.havaldock.data

import androidx.annotation.DrawableRes
import br.com.redesurftank.havaldock.R
import java.util.Locale

/** Chaves do IntelligentVehicleControlService (do CarConstants do Impulse / nota do vault). */
object DockKeys {
    const val DRIVER_TEMP = "car.hvac.driver_temperature"
    const val PASS_TEMP = "car.hvac.pass_temperature"
    const val FRONT_TEMP_RANGE = "car.hvac.front_temperature_range"
    const val FAN_SPEED = "car.hvac.fan_speed"
    const val FAN_RANGE = "car.hvac.fan_speed_range"
    const val ACMAX = "car.hvac.acmax_enable"
    const val AUTO = "car.hvac.auto_enable"
    const val SYNC = "car.hvac.sync_enable"
    const val CYCLE_MODE = "car.hvac.cycle_mode"
    const val DRIVER_SEAT_VENT = "car.comfort_setting.driver_seat_ventilation_level"
    const val PASS_SEAT_VENT = "car.comfort_setting.passenger_seat_ventilation_level"
    const val SEAT_VENT_MAX = "car.comfort_setting.seat_ventilation_max_level"
    const val DRIVE_MODE = "car.drive_setting.drive_mode"
    const val STEER_MODE = "car.drive_setting.steering_wheel_assist_mode"
    const val REGEN_LEVEL = "car.ev_setting.energy_recovery_level"
    const val MEDIA_VOLUME = "sys.settings.audio.media_volume"
    const val MEDIA_VOLUME_RANGE = "sys.settings.audio.media_volume_range"
}

/** Cores do tema v2 (ARGB int — sem dependência de android no data layer). */
object DockColors {
    const val CYAN = 0xFF2DE0F0.toInt()
    const val GREEN = 0xFF36E05A.toInt()
    const val RED = 0xFFFF4D4D.toInt()
    const val AMBER = 0xFFFFC23C.toInt()
    const val WHITE = 0xFFEEF4F8.toInt()
}

private fun parseMax(s: String?): Double? {
    if (s == null) return null
    return Regex("-?\\d+(\\.\\d+)?").findAll(s).mapNotNull { it.value.toDoubleOrNull() }.maxOrNull()
}

/** Estado de render lido do veículo (campos usados variam por tipo de controle). */
data class RenderState(
    val text: String? = null,
    val ratio: Float = 0f,
    val on: Boolean = false,
    val color: Int = DockColors.CYAN,
    val bars: Int = 0,
)

sealed class Control(val id: String, val section: Int, val label: String) {
    abstract fun render(): RenderState
}

/** Temperatura: exibida com setas ‹ ›; escreve o valor float direto (ex.: "22.5"). */
class Temp(id: String, section: Int, label: String, val key: String,
          val min: Double, val max: Double, val step: Double, val rangeKey: String?) :
    Control(id, section, label) {
    override fun render() = RenderState(text = read()?.let { fmt(it) + "°" } ?: "—°")
    private fun read() = VehicleClient.getData(key)?.trim()?.toDoubleOrNull()
    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
    fun nudge(dir: Int) {
        val cur = read() ?: return
        val hi = rangeKey?.let { parseMax(VehicleClient.getData(it)) } ?: max
        VehicleClient.set(key, fmt((cur + dir * step).coerceIn(min, hi)))
    }
}

/** Banco/ventilador: ícone + sublinhado de nível; toque incrementa e dá a volta. */
class Level(id: String, section: Int, label: String, @DrawableRes val icon: Int,
           val key: String, val max: Int, val rangeKey: String?) :
    Control(id, section, label) {
    private fun value() = VehicleClient.getData(key)?.trim()?.toIntOrNull() ?: 0
    private fun hi() = rangeKey?.let { parseMax(VehicleClient.getData(it))?.toInt() } ?: max
    override fun render(): RenderState {
        val m = hi().coerceAtLeast(1)
        return RenderState(ratio = value().coerceIn(0, m).toFloat() / m)
    }
    fun cycle() {
        val m = hi().coerceAtLeast(1)
        val next = if (value() >= m) 0 else value() + 1
        VehicleClient.set(key, next.toString())
    }
}

/** Volume: ícone + sublinhado; abre popup vertical com −/+ e arraste. */
class Volume(id: String, section: Int, label: String, @DrawableRes val icon: Int,
            val key: String, val max: Int, val rangeKey: String?) :
    Control(id, section, label) {
    fun value() = VehicleClient.getData(key)?.trim()?.toIntOrNull() ?: 0
    fun hi() = (rangeKey?.let { parseMax(VehicleClient.getData(it))?.toInt() } ?: max).coerceAtLeast(1)
    override fun render() = RenderState(ratio = value().coerceIn(0, hi()).toFloat() / hi(), text = value().toString())
    fun set(v: Int) = VehicleClient.set(key, v.coerceIn(0, hi()).toString())
}

/** Toggle de texto (MAX / AUTO / SYNC): on em ciano + sublinhado. */
class TxtToggle(id: String, section: Int, label: String, val key: String) :
    Control(id, section, label) {
    fun isOn() = VehicleClient.getData(key)?.trim() == "1"
    override fun render() = RenderState(on = isOn())
    fun flip() = VehicleClient.set(key, if (isOn()) "0" else "1")
}

/** Toggle de ícone (recirculador): cycle_mode 1=recirc, 0=externo. */
class IconToggle(id: String, section: Int, label: String, @DrawableRes val icon: Int,
                val key: String, val onV: String, val offV: String) :
    Control(id, section, label) {
    fun isOn() = VehicleClient.getData(key)?.trim() == onV
    override fun render() = RenderState(on = isOn())
    fun flip() = VehicleClient.set(key, if (isOn()) offV else onV)
}

/** Modo (condução/direção): ícone + label colorido por estado; toque cicla. */
class Mode(id: String, section: Int, label: String, @DrawableRes val icon: Int,
          val key: String, val order: List<Int>, val labels: Map<Int, String>, val colors: Map<Int, Int>) :
    Control(id, section, label) {
    private fun cur() = VehicleClient.getData(key)?.trim()?.toIntOrNull()
    override fun render(): RenderState {
        val v = cur()
        return RenderState(text = (labels[v] ?: "—").uppercase(), color = colors[v] ?: DockColors.CYAN)
    }
    fun next() {
        val idx = order.indexOf(cur())
        VehicleClient.set(key, order[(idx + 1).mod(order.size)].toString())
    }
}

/** Regeneração: raio colorido por nível (verde=Baixo, amarelo=Normal, vermelho=Alto) + barras. */
class Regen(id: String, section: Int, label: String, @DrawableRes val icon: Int,
           val key: String, val order: List<Int>) :
    Control(id, section, label) {
    // value -> (barras, cor): 2=Baixo(1,verde), 0=Normal(2,amarelo), 1=Alto(3,vermelho)
    private val map = mapOf(
        2 to Pair(1, DockColors.GREEN),
        0 to Pair(2, DockColors.AMBER),
        1 to Pair(3, DockColors.RED),
    )
    private fun cur() = VehicleClient.getData(key)?.trim()?.toIntOrNull()
    override fun render(): RenderState {
        val (bars, color) = map[cur()] ?: Pair(0, DockColors.GREEN)
        return RenderState(bars = bars, color = color)
    }
    fun next() {
        val idx = order.indexOf(cur())
        VehicleClient.set(key, order[(idx + 1).mod(order.size)].toString())
    }
}

object DockControls {
    val ALL: List<Control> = listOf(
        // ----- ESQUERDA (clima) -----
        Temp("tempD", 0, "Temp. motorista", DockKeys.DRIVER_TEMP, 16.0, 32.0, 0.5, DockKeys.FRONT_TEMP_RANGE),
        Level("ventD", 0, "Ventil. motorista", R.drawable.ic_seat, DockKeys.DRIVER_SEAT_VENT, 3, DockKeys.SEAT_VENT_MAX),
        Level("fan", 0, "Veloc. ar-cond.", R.drawable.ic_fan, DockKeys.FAN_SPEED, 7, DockKeys.FAN_RANGE),
        TxtToggle("max", 0, "MAX", DockKeys.ACMAX),
        TxtToggle("auto", 0, "AUTO", DockKeys.AUTO),
        TxtToggle("sync", 0, "SYNC", DockKeys.SYNC),
        IconToggle("recirc", 0, "Recirculador", R.drawable.ic_recirc, DockKeys.CYCLE_MODE, "1", "0"),
        // ----- CENTRO (condução) -----
        Mode("drive", 1, "Modo condução", R.drawable.ic_car, DockKeys.DRIVE_MODE,
            listOf(0, 2, 1),
            mapOf(0 to "Normal", 1 to "Sport", 2 to "Eco", 3 to "Neve", 4 to "Areia", 5 to "Lama", 11 to "AWD"),
            mapOf(0 to DockColors.CYAN, 1 to DockColors.RED, 2 to DockColors.GREEN, 3 to DockColors.CYAN)),
        Mode("steer", 1, "Modo direção", R.drawable.ic_steer, DockKeys.STEER_MODE,
            listOf(2, 0, 1),
            mapOf(2 to "Conforto", 0 to "Normal", 1 to "Esportiva"),
            mapOf(2 to DockColors.CYAN, 0 to DockColors.WHITE, 1 to DockColors.RED)),
        Regen("regen", 1, "Regeneração", R.drawable.ic_bolt, DockKeys.REGEN_LEVEL, listOf(2, 0, 1)),
        // ----- DIREITA (passageiro + volume) -----
        Temp("tempP", 2, "Temp. passageiro", DockKeys.PASS_TEMP, 16.0, 32.0, 0.5, DockKeys.FRONT_TEMP_RANGE),
        Level("ventP", 2, "Ventil. passageiro", R.drawable.ic_seat, DockKeys.PASS_SEAT_VENT, 3, DockKeys.SEAT_VENT_MAX),
        Volume("vol", 2, "Volume rádio", R.drawable.ic_volume, DockKeys.MEDIA_VOLUME, 30, DockKeys.MEDIA_VOLUME_RANGE),
    )

    val MONITORED: List<String> = listOf(
        DockKeys.DRIVER_TEMP, DockKeys.PASS_TEMP, DockKeys.FAN_SPEED, DockKeys.DRIVER_SEAT_VENT,
        DockKeys.PASS_SEAT_VENT, DockKeys.ACMAX, DockKeys.AUTO, DockKeys.SYNC, DockKeys.CYCLE_MODE,
        DockKeys.DRIVE_MODE, DockKeys.STEER_MODE, DockKeys.REGEN_LEVEL, DockKeys.MEDIA_VOLUME,
    )
}
