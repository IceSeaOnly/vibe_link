package com.vibelink.client

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import android.view.TextureView
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class H264VideoStreamPlayer(
    private val textureView: TextureView
) : Closeable {
    private val closed = AtomicBoolean(false)
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var frameBuffer: H264FrameBuffer? = null
    private var readerThread: Thread? = null

    fun play(reader: H264StreamReader, width: Int, height: Int, onFrameRendered: () -> Unit) {
        val targetSurface = waitForSurface(width, height)
        surface = targetSurface
        val buffer = H264FrameBuffer(4)
        frameBuffer = buffer
        readerThread = Thread({
            try {
                reader.readFrames { payload ->
                    if (!closed.get()) buffer.push(payload)
                }
            } finally {
                buffer.close()
            }
        }, "vibelink-h264-reader").apply {
            isDaemon = true
            start()
        }

        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = decoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 * 1024 * 1024)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, 20)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }
        decoder.configure(format, targetSurface, null, 0)
        decoder.start()

        try {
            while (!closed.get()) {
                val payload = buffer.take() ?: break
                drainOutput(decoder, onFrameRendered)
                queuePayload(decoder, payload)
                drainOutput(decoder, onFrameRendered)
            }
        } finally {
            close()
        }
    }

    private fun queuePayload(decoder: MediaCodec, payload: ByteArray) {
        val inputIndex = decoder.dequeueInputBuffer(10_000)
        if (inputIndex < 0) return
        val buffer = decoder.getInputBuffer(inputIndex) ?: return
        buffer.clear()
        if (payload.size > buffer.capacity()) {
            decoder.queueInputBuffer(inputIndex, 0, 0, System.nanoTime() / 1000L, 0)
            throw IllegalStateException("h264 sample too large: ${payload.size}")
        }
        buffer.put(payload)
        decoder.queueInputBuffer(inputIndex, 0, payload.size, System.nanoTime() / 1000L, 0)
    }

    private fun drainOutput(decoder: MediaCodec, onFrameRendered: () -> Unit) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            when (val outputIndex = decoder.dequeueOutputBuffer(info, 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> {
                    if (outputIndex >= 0) {
                        decoder.releaseOutputBuffer(outputIndex, true)
                        onFrameRendered()
                    }
                }
            }
        }
    }

    private fun waitForSurface(width: Int, height: Int): Surface {
        textureView.surfaceTexture?.let {
            it.setDefaultBufferSize(width, height)
            return Surface(it)
        }
        val latch = CountDownLatch(1)
        var surfaceTexture: SurfaceTexture? = null
        textureView.post {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    surfaceTexture = surface
                    latch.countDown()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw IllegalStateException("video surface unavailable")
        }
        val texture = surfaceTexture ?: throw IllegalStateException("video surface unavailable")
        texture.setDefaultBufferSize(width, height)
        return Surface(texture)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            codec?.stop()
        }
        runCatching {
            codec?.release()
        }
        codec = null
        frameBuffer?.close()
        frameBuffer = null
        readerThread?.interrupt()
        readerThread = null
        surface?.release()
        surface = null
    }
}
