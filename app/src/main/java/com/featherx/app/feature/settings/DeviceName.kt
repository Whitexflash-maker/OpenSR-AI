package com.featherx.app.feature.settings

/** Many phones report the brand inside the model name already ("TECNO" + "TECNO KM8n"). */
fun deviceName(manufacturer: String, model: String): String {
    val maker = manufacturer.trim()
    val name = model.trim()
    return when {
        name.isEmpty() -> maker.ifEmpty { "Unknown device" }
        maker.isEmpty() -> name
        name.startsWith(maker, ignoreCase = true) -> name
        else -> "$maker $name"
    }
}
