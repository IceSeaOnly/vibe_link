package com.vibelink.client

data class HealthInfo(
    val name: String,
    val version: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val scale: Double,
    val displays: List<RemoteDisplay> = emptyList()
)

data class RemoteDisplay(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Double,
    val isMain: Boolean
)

enum class InteractionMode {
    SCREEN,
    POINTER,
    TRACKPAD
}

data class QuickText(
    val id: String,
    val name: String,
    val group: String,
    val content: String
)

data class ControlButton(
    val id: String,
    val label: String,
    val type: String
)

data class ClientConfig(
    val controlButtons: List<ControlButton>,
    val quickTexts: List<QuickText>,
    val commands: List<QuickCommand>,
    val shortcutButtons: List<ShortcutButton>,
    val updatedAt: String
)

data class QuickCommand(
    val id: String,
    val name: String,
    val command: String,
    val workingDirectory: String,
    val requiresConfirmation: Boolean
)

data class CommandRun(
    val id: String,
    val status: String,
    val exitCode: Int?,
    val output: String
)

data class ShortcutButton(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val screenWidth: Double,
    val screenHeight: Double,
    val requiresConfirmation: Boolean
)

data class ImageGeometry(
    val viewWidth: Int,
    val viewHeight: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

object CoordinateMapper {
    fun fitCenterGeometry(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): ImageGeometry {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return ImageGeometry(viewWidth, viewHeight, imageWidth, imageHeight, 1f, 0f, 0f)
        }
        val scale = minOf(viewWidth.toFloat() / imageWidth.toFloat(), viewHeight.toFloat() / imageHeight.toFloat())
        val drawnWidth = imageWidth * scale
        val drawnHeight = imageHeight * scale
        return ImageGeometry(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            scale = scale,
            offsetX = (viewWidth - drawnWidth) / 2f,
            offsetY = (viewHeight - drawnHeight) / 2f
        )
    }

    fun mapViewToImage(x: Float, y: Float, geometry: ImageGeometry): Pair<Int, Int>? {
        if (geometry.imageWidth <= 0 || geometry.imageHeight <= 0 || geometry.scale <= 0f) return null
        val imageX = ((x - geometry.offsetX) / geometry.scale).toInt()
        val imageY = ((y - geometry.offsetY) / geometry.scale).toInt()
        if (imageX !in 0 until geometry.imageWidth || imageY !in 0 until geometry.imageHeight) return null
        return imageX to imageY
    }
}
