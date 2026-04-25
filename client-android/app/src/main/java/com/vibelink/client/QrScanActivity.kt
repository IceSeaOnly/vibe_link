package com.vibelink.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.HybridBinarizer

class QrScanActivity : Activity() {
    private lateinit var textureView: TextureView
    private lateinit var statusText: TextView
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var decoding = false
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textureView = TextureView(this)
        statusText = TextView(this).apply {
            text = "Scan VibeLink pairing QR"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x88000000.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
        }
        val root = FrameLayout(this)
        root.addView(textureView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(statusText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
        } else {
            startCameraWhenReady()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCameraWhenReady()
        } else {
            finish()
        }
    }

    private fun startCameraWhenReady() {
        thread = HandlerThread("qr-scan").also { it.start() }
        handler = Handler(thread!!.looper)
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = openCamera()
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
    }

    private fun openCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return finish()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                startPreview(device)
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                finish()
            }
        }, handler)
    }

    private fun startPreview(device: CameraDevice) {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(640, 480)
        val previewSurface = Surface(texture)
        val reader = ImageReader.newInstance(640, 480, android.graphics.ImageFormat.YUV_420_888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (decoding) {
                image.close()
                return@setOnImageAvailableListener
            }
            decoding = true
            try {
                decodeImage(image)
            } finally {
                image.close()
                decoding = false
            }
        }, handler)

        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(reader.surface)
        }
        device.createCaptureSession(listOf(previewSurface, reader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(captureSession: CameraCaptureSession) {
                session = captureSession
                captureSession.setRepeatingRequest(request.build(), null, handler)
            }

            override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                finish()
            }
        }, handler)
    }

    private fun decodeImage(image: android.media.Image) {
        val plane = image.planes.firstOrNull() ?: return
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val buffer = plane.buffer
        val y = ByteArray(width * height)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(y, row * width, width)
        }
        val source = PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = runCatching { reader.decodeWithState(bitmap) }.getOrNull()
        reader.reset()
        val text = result?.text ?: return
        if (text.startsWith("vibelink://pair")) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PAIRING_URI, text))
            finish()
        }
    }

    override fun onDestroy() {
        session?.close()
        camera?.close()
        imageReader?.close()
        thread?.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAIRING_URI = "pairing_uri"
    }
}
