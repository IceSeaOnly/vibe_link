package com.vibelink.client

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

class H264StreamReader private constructor(
    private val socket: Socket,
    private val input: BufferedInputStream
) : Closeable {
    fun readFrames(onFrame: (ByteArray) -> Unit) {
        while (!socket.isClosed) {
            val first = input.read()
            if (first == -1) return
            val second = input.read()
            if (second == -1) return
            val opcode = first and 0x0f
            var length = second and 0x7f
            if (length == 126) {
                length = (input.readRequired() shl 8) or input.readRequired()
            } else if (length == 127) {
                var longLength = 0L
                repeat(8) { longLength = (longLength shl 8) or input.readRequired().toLong() }
                if (longLength > Int.MAX_VALUE) throw IllegalStateException("websocket frame too large")
                length = longLength.toInt()
            }
            val masked = second and 0x80 != 0
            val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
            val payload = ByteArray(length)
            input.readFully(payload)
            if (mask != null) {
                for (index in payload.indices) payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
            }
            if (opcode == 8) return
            if (opcode == 2 && payload.isNotEmpty()) onFrame(payload)
        }
    }

    override fun close() {
        socket.close()
    }

    companion object {
        fun connect(baseUrl: String, token: String, displayId: String?): H264StreamReader {
            val base = URI(baseUrl.trim().trimEnd('/'))
            val scheme = if (base.scheme == "https") "wss" else "ws"
            if (scheme == "wss") throw IllegalStateException("wss is not supported in the MVP client")
            val host = base.host ?: throw IllegalArgumentException("missing host")
            val port = if (base.port > 0) base.port else if (base.scheme == "https") 443 else 80
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val displayQuery = displayId?.takeIf { it.isNotBlank() }?.let { "&displayId=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
            val path = "/stream-h264?token=$encodedToken$displayQuery"
            val socket = Socket(host, port).apply {
                soTimeout = 15000
                tcpNoDelay = true
                receiveBufferSize = 256 * 1024
            }
            val output = BufferedOutputStream(socket.getOutputStream())
            val input = BufferedInputStream(socket.getInputStream())
            val key = randomKey()
            val request = "GET $path HTTP/1.1\r\n" +
                "Host: $host:$port\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n"
            output.write(request.toByteArray(Charsets.US_ASCII))
            output.flush()
            val response = readHeaders(input)
            if (!response.startsWith("HTTP/1.1 101")) {
                socket.close()
                throw IllegalStateException("h264 websocket handshake failed: ${response.lineSequence().firstOrNull().orEmpty()}")
            }
            return H264StreamReader(socket, input)
        }

        private fun randomKey(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return Base64.getEncoder().encodeToString(bytes)
        }

        private fun readHeaders(input: BufferedInputStream): String {
            val bytes = ArrayList<Byte>()
            while (true) {
                val value = input.read()
                if (value == -1) break
                bytes.add(value.toByte())
                val size = bytes.size
                if (size >= 4 && bytes[size - 4] == '\r'.code.toByte() && bytes[size - 3] == '\n'.code.toByte() && bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()) {
                    break
                }
            }
            return bytes.toByteArray().toString(Charsets.US_ASCII)
        }
    }
}

private fun BufferedInputStream.readRequired(): Int {
    val value = read()
    if (value == -1) throw IllegalStateException("h264 websocket stream ended")
    return value
}

private fun BufferedInputStream.readFully(target: ByteArray) {
    var offset = 0
    while (offset < target.size) {
        val read = read(target, offset, target.size - offset)
        if (read == -1) throw IllegalStateException("h264 websocket stream ended")
        offset += read
    }
}
