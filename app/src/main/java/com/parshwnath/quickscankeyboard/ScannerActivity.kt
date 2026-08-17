package com.parshwnath.quickscankeyboard

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var lastScan: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanner: BarcodeScanner
    private var camera: Camera? = null
    private var torchOn = false
    private var lastValue = ""
    private var lastScanAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        status = findViewById(R.id.scannerStatus)
        lastScan = findViewById(R.id.lastScan)
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.torchButton).setOnClickListener { toggleTorch() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
    }

    private fun buildScanner(): BarcodeScanner {
        val zoomCallback = ZoomSuggestionOptions.ZoomCallback { zoomRatio ->
            val c = camera ?: return@ZoomCallback false
            c.cameraControl.setZoomRatio(zoomRatio)
            true
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX
            )
            .enableAllPotentialBarcodes()
            .setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder(zoomCallback)
                    .setMaxSupportedZoomRatio(6.0f)
                    .build()
            )
            .build()

        return BarcodeScanning.getClient(options)
    }

    private fun startCamera() {
        status.text = "Starting high-resolution scanner…"
        scanner = buildScanner()

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy -> processFrame(imageProxy) }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, analysis)

            camera?.cameraControl?.setLinearZoom(0f)
            status.text = "READY — point at any QR"
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val decoded = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (decoded != null) handleDecoded(decoded)
            }
            .addOnFailureListener {
                // Keep scanning. A bad frame must never stop the analyzer.
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleDecoded(value: String) {
        val now = System.currentTimeMillis()
        // Prevent repeated frames of the same QR from creating duplicate bills.
        if (value == lastValue && now - lastScanAt < 900L) return
        lastValue = value
        lastScanAt = now

        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        lastScan.text = value
        status.text = "SCANNED — sending…"

        QuickScanKeyboardService.current?.sendScanResult(value)

        // Give the remote application a short moment to consume the Enter event,
        // then return to the camera for the next product.
        mainHandler.postDelayed({
            if (!isFinishing) status.text = "READY — next QR"
        }, 180L)
    }

    private fun toggleTorch() {
        val c = camera ?: return
        if (!c.cameraInfo.hasFlashUnit()) {
            status.text = "This camera has no torch"
            return
        }
        torchOn = !torchOn
        c.cameraControl.enableTorch(torchOn)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            status.text = "Camera permission is required"
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        if (::scanner.isInitialized) scanner.close()
        tone.release()
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_REQUEST = 42
    }
}
