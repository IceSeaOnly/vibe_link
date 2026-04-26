package com.vibelink.client

object CaptureSourceUi {
    fun preferredSource(info: CaptureSourcesInfo): RemoteCaptureSource? {
        return info.selected ?: info.sources.firstOrNull { it.isMain } ?: info.sources.firstOrNull()
    }

    fun displayIdFor(source: RemoteCaptureSource?): String? {
        val id = source?.id ?: return null
        return if (source.type == "display" && id.startsWith("display:")) {
            id.removePrefix("display:")
        } else {
            null
        }
    }

    fun label(source: RemoteCaptureSource): String {
        val dims = if (source.width > 0 && source.height > 0) " (${source.width}x${source.height})" else ""
        return if (source.type == "window") {
            val app = source.appName?.takeIf { it.isNotBlank() }
            if (app == null || app == source.name) {
                "${source.name}$dims"
            } else {
                "$app · ${source.name}$dims"
            }
        } else {
            "${source.name}$dims"
        }
    }

    fun pickerTitle(source: RemoteCaptureSource): String {
        return source.name.ifBlank { label(source) }
    }

    fun pickerSubtitle(source: RemoteCaptureSource): String {
        val dims = dimensions(source)
        return if (source.type == "window") {
            val app = source.appName?.takeIf { it.isNotBlank() && it != source.name }
            listOfNotNull(app, dims).joinToString(" · ")
        } else {
            dims
        }
    }

    fun pickerBadge(source: RemoteCaptureSource): String {
        return if (source.type == "window") "WIN" else "DSP"
    }

    private fun dimensions(source: RemoteCaptureSource): String {
        return if (source.width > 0 && source.height > 0) "${source.width}x${source.height}" else ""
    }
}
