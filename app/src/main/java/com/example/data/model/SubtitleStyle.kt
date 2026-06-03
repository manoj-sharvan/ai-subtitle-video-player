package com.example.data.model

data class SubtitleStyle(
    val fontSizeSp: Float = 16f,
    val fontFamily: String = "SansSerif", // SansSerif, Monospace, Serif, Cursive
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#000000",
    val backgroundOpacity: Float = 0.5f,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM
)

enum class SubtitlePosition {
    TOP, CENTER, BOTTOM
}
