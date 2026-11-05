package com.novaforge.fpsplus.monitor

data class ChipTemperature(val celsius: Float, val source: String)

/** Best-effort parser for the temperature sensors exposed by Android's ThermalService dump. */
object ThermalParser {
    fun parse(dump: String): ChipTemperature? {
        val candidates = Regex("Temperature\\{([^}]*)}")
            .findAll(dump)
            .mapNotNull { match ->
                val block = match.groupValues[1]
                val value = Regex("mValue=([-+]?[0-9]*\\.?[0-9]+)").find(block)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: return@mapNotNull null
                val type = Regex("mType=(\\d+)").find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val name = Regex("mName=['\\\"]?([^,'\\\"}]+)").find(block)?.groupValues?.getOrNull(1).orEmpty()
                val source = when (type) {
                    0 -> "CPU"
                    1 -> "GPU"
                    else -> when {
                        name.contains("cpu", ignoreCase = true) -> "CPU"
                        name.contains("gpu", ignoreCase = true) -> "GPU"
                        else -> ""
                    }
                }
                if (source.isBlank() || value !in 1f..150f) null else ChipTemperature(value, source)
            }
            .toList()
        val cpu = candidates.filter { it.source == "CPU" }.maxByOrNull { it.celsius }
        val gpu = candidates.filter { it.source == "GPU" }.maxByOrNull { it.celsius }
        return cpu ?: gpu
    }
}
