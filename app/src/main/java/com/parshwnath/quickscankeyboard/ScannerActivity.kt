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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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

    /**
     * True once a barcode has been successfully decoded and sent.
     * The camera analyzer runs continuously on a background thread,
     * so without this flag it can fire handleDecoded() again for the
     * same barcode while we're still in the process of finishing.
     */
    private var handled = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val tone by lazy {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
    }

    /*
     * Modern camera permission handler.
     * This replaces the old onRequestPermissionsResult()
     * which was causing the Kotlin compilation error.
     */
    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                status.text = "Camera permission is required"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_scanner)

        previewView = findViewById(R.id.previewView)
        status = findViewById(R.id.scannerStatus)
        lastScan = findViewById(R.id.lastScan)

        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<Button>(R.id.closeButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.torchButton).setOnClickListener {
            toggleTorch()
        }

        /*
         * Check camera permission.
         */
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun buildScanner(): BarcodeScanner {

        /*
         * ML Kit can suggest zooming when the barcode
         * is too small in the camera frame.
         */
        val zoomCallback =
            ZoomSuggestionOptions.ZoomCallback { zoomRatio ->

                val c = camera ?: return@ZoomCallback false

                c.cameraControl.setZoomRatio(zoomRatio)

                true
            }

        val options =
            BarcodeScannerOptions.Builder()
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

                /*
                 * Ask ML Kit to return potential barcodes
                 * even when it cannot completely decode them.
                 */
                .enableAllPotentialBarcodes()

                /*
                 * Allow ML Kit to suggest zooming for
                 * small barcodes.
                 */
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

        val providerFuture =
            ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({

            val provider = providerFuture.get()

            /*
             * Camera preview.
             */
            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider =
                            previewView.surfaceProvider
                    }

            /*
             * Request a high-resolution camera stream.
             *
             * This is especially useful for your use case:
             * small QR codes on silver ornaments,
             * plastic wrapping and covers.
             */
            val resolutionSelector =
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(
                                1920,
                                1080
                            ),
                            ResolutionStrategy
                                .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

            /*
             * Image analysis.
             */
            val analysis =
                ImageAnalysis.Builder()
                    .setResolutionSelector(
                        resolutionSelector
                    )
                    .setBackpressureStrategy(
                        ImageAnalysis
                            .STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            analysis.setAnalyzer(
                cameraExecutor
            ) { imageProxy ->

                processFrame(imageProxy)
            }

            /*
             * Use the rear camera.
             */
            val selector =
                CameraSelector.DEFAULT_BACK_CAMERA

            /*
             * Remove any previous camera binding.
             */
            provider.unbindAll()

            /*
             * Start camera.
             */
            camera =
                provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    analysis
                )

            /*
             * Start at normal zoom.
             */
            camera?.cameraControl?.setLinearZoom(0f)

            status.text = "READY — point at any QR"

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(
        imageProxy: ImageProxy
    ) {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        /*
         * Convert CameraX image to ML Kit image.
         */
        val image =
            InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

        /*
         * Send frame to ML Kit.
         */
        scanner.process(image)

            .addOnSuccessListener { barcodes ->

                /*
                 * Find the first barcode with
                 * an actual decoded value.
                 */
                val decoded =
                    barcodes
                        .firstOrNull {
                            !it.rawValue.isNullOrBlank()
                        }
                        ?.rawValue

                if (decoded != null) {
                    handleDecoded(decoded)
                }
            }

            .addOnFailureListener {
                /*
                 * Keep scanning.
                 *
                 * A bad frame must never stop
                 * the camera analyzer.
                 */
            }

            .addOnCompleteListener {

                /*
                 * VERY IMPORTANT:
                 * Always close ImageProxy.
                 */
                imageProxy.close()
            }
    }

    private fun handleDecoded(
        value: String
    ) {

        /*
         * Single-shot scanning: once we've successfully handled
         * one barcode, ignore any further frames. Without this,
         * the background analyzer can keep calling handleDecoded()
         * for a few more frames while we're in the middle of
         * finishing this Activity.
         */
        if (handled) {
            return
        }
        handled = true

        /*
         * Beep.
         */
        tone.startTone(
            ToneGenerator.TONE_PROP_BEEP,
            70
        )

        /*
         * Show scanned value.
         */
        lastScan.text = value

        status.text = "SCANNED — sending…"

        /*
         * Send the QR value to the keyboard service.
         *
         * The service handles typing it into
         * the PC through AnyDesk.
         */
        QuickScanKeyboardService
            .current
            ?.sendScanResult(value)

        /*
         * Give the remote application a short moment to consume
         * the keystrokes and Enter event, then automatically
         * close the scanner and return to whatever app (AnyDesk)
         * was open before — no manual "CLOSE" tap needed.
         */
        mainHandler.postDelayed({

            if (!isFinishing) {
                finish()
            }

        }, 180L)
    }

    private fun toggleTorch() {

        val c = camera ?: return

        /*
         * Check whether the phone has a flash.
         */
        if (!c.cameraInfo.hasFlashUnit()) {

            status.text =
                "This camera has no torch"

            return
        }

        torchOn = !torchOn

        c.cameraControl.enableTorch(
            torchOn
        )
    }

    override fun onDestroy() {

        /*
         * Stop camera analysis thread.
         */
        cameraExecutor.shutdown()

        /*
         * Close ML Kit scanner.
         */
        if (::scanner.isInitialized) {
            scanner.close()
        }

        /*
         * Release beep generator.
         */
        tone.release()

        super.onDestroy()
    }
}