package com.featherx.app.feature.protools

/*
 * Pure Kotlin (no Android classes) so it can be unit-tested on the build server.
 */

enum class CrosshairStyle(val label: String) {
    DOT("Dot"),
    CROSS("Cross"),
    CIRCLE("Ring"),
}

enum class CrosshairColor(val label: String, val argb: Int) {
    RED("Red", 0xFFFF3B30.toInt()),
    GREEN("Green", 0xFF34C759.toInt()),
    CYAN("Cyan", 0xFF00E5FF.toInt()),
    WHITE("White", 0xFFFFFFFF.toInt()),
}

/**
 * A screen filter can only be drawn ON TOP of the picture: it can tint or dim, it cannot add light.
 * "Lift" therefore puts a light veil over the screen, so near-black areas become dark grey
 * (easier to see) at the price of lower contrast. The hint text says this honestly.
 */
enum class FilterMode(val label: String, val rgb: Int, val hint: String) {
    LIFT("Lift", 0xFFFFFF, "Light veil: dark areas look lighter, overall contrast drops."),
    WARM("Warm", 0xFFA040, "Amber tint: softer on the eyes at night."),
    DIM("Dim", 0x000000, "Black veil: lowers brightness below the system minimum."),
}

data class OverlaySettings(
    val crosshairOn: Boolean = false,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.DOT,
    val crosshairColor: CrosshairColor = CrosshairColor.RED,
    val crosshairSizeDp: Int = DEFAULT_CROSSHAIR_DP,
    val filterOn: Boolean = false,
    val filterMode: FilterMode = FilterMode.LIFT,
    /** 0..100 */
    val filterStrength: Int = DEFAULT_STRENGTH,
) {
    fun sanitized(): OverlaySettings = copy(
        crosshairSizeDp = crosshairSizeDp.coerceIn(MIN_CROSSHAIR_DP, MAX_CROSSHAIR_DP),
        filterStrength = filterStrength.coerceIn(0, 100),
    )

    /** ARGB colour of the tint view. Alpha never exceeds [MAX_TINT_ALPHA], so the screen stays usable. */
    fun filterArgb(): Int {
        val alpha = filterStrength.coerceIn(0, 100) * MAX_TINT_ALPHA / 100
        return (alpha shl 24) or (filterMode.rgb and 0xFFFFFF)
    }

    companion object {
        const val MIN_CROSSHAIR_DP = 8
        const val MAX_CROSSHAIR_DP = 48
        const val DEFAULT_CROSSHAIR_DP = 20
        const val DEFAULT_STRENGTH = 30
        const val MAX_TINT_ALPHA = 153 // 60 %
    }
}

inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
