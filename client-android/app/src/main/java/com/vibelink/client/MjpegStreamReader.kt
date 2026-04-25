package com.vibelink.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MjpegStreamReader(private val input: InputStream) {
    fun readFrames(onFrame: (Bitmap) -> Unit) {
        val stream = if (input is BufferedInputStream) input else BufferedInputStream(input)
        while (!Thread.currentThread().isInterrupted) {
            val headers = readHeaders(stream) ?: return
            val contentLength = headers["content-length"]?.toIntOrNull()
            val jpeg = if (contentLength != null && contentLength > 0) {
                stream.readExactly(contentLength)
            } else {
                readJpegByMarkers(stream) ?: return
            }
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            if (bitmap != null) onFrame(bitmap)
        }
    }

    private fun readHeaders(stream: BufferedInputStream): Map<String, String>? {
        val line = readLine(stream) ?: return null
        if (!line.startsWith("--")) return readHeaders(stream)
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val headerLine = readLine(stream) ?: return null
            if (headerLine.isEmpty()) return headers
            val separator = headerLine.indexOf(':')
            if (separator > 0) {
                headers[headerLine.substring(0, separator).trim().lowercase()] =
                    headerLine.substring(separator + 1).trim()
            }
        }
    }

    private fun readLine(stream: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = stream.read()
            if (byte == -1) return if (buffer.size() == 0) null else buffer.toString("ISO-8859-1")
            if (byte == '\n'.code) {
                val line = buffer.toString("ISO-8859-1")
                return line.trimEnd('\r')
            }
            buffer.write(byte)
        }
    }

    private fun readJpegByMarkers(stream: BufferedInputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val byte = stream.read()
            if (byte == -1) return null
            if (previous == 0xFF && byte == 0xD8) {
                output.write(0xFF)
                output.write(0xD8)
                break
            }
            previous = byte
        }
        previous = -1
        while (true) {
            val byte = stream.read()
            if (byte == -1) return null
            output.write(byte)
            if (previous == 0xFF && byte == 0xD9) return output.toByteArray()
            previous = byte
        }
    }

    private fun BufferedInputStream.readExactly(length: Int): ByteArray {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(data, offset, length - offset)
            if (read == -1) throw IllegalStateException("stream ended while reading frame")
            offset += read
        }
        return data
    }
}
