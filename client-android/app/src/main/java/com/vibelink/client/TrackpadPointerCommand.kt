package com.vibelink.client

data class AbsolutePointerTarget(
    val x: Int,
    val y: Int,
    val imageWidth: Int,
    val imageHeight: Int
)

object TrackpadPointerCommand {
    fun target(cursorX: Int?, cursorY: Int?, imageWidth: Int, imageHeight: Int): AbsolutePointerTarget? {
        val x = cursorX ?: return null
        val y = cursorY ?: return null
        if (imageWidth <= 0 || imageHeight <= 0) return null
        return AbsolutePointerTarget(
            x = x.coerceIn(0, imageWidth - 1),
            y = y.coerceIn(0, imageHeight - 1),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }
}
