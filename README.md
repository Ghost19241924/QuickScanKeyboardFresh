# QuickScan Keyboard

A dedicated Android QR/barcode scanner designed to work as an Android input method (keyboard).

## Intended workflow

1. Open the PC billing/scanner window through AnyDesk.
2. Focus the product/QR input field on the PC.
3. On the phone, select **QuickScan Keyboard** as the active keyboard.
4. Tap **SCAN QR**.
5. Point the camera at a product QR.
6. The app scans the code, commits the text through the active input connection, and sends **Enter** automatically.
7. The camera immediately becomes ready for the next product.

## Scanner optimizations

- Full camera frame; no center-only scan box.
- QR + common 1D/2D barcode formats.
- 1920x1080 analysis when supported.
- ML Kit bundled barcode model, so scanning works without waiting for a model download.
- ML Kit potential-barcode detection.
- ML Kit automatic zoom suggestions, up to 6x where the camera supports it.
- Continuous scanning with a short duplicate guard to prevent the same product from being entered repeatedly.
- Torch button.
- Beep on successful scan.

Google's current ML Kit documentation specifically recommends higher-resolution input such as 1280x720 or 1920x1080 for barcodes that occupy a smaller part of the camera image, and documents potential-barcode detection and auto-zoom. The project uses the bundled barcode model so the model is packaged in the APK. See the official docs: https://developers.google.com/ml-kit/vision/barcode-scanning/android

## Build on GitHub

Push this folder to a GitHub repository. The included workflow builds the debug APK automatically on every push to `main`, and it can also be run manually from **Actions → Build APK → Run workflow**.

Download the generated APK from the workflow's **Artifacts** section.

## Important AnyDesk note

This app sends the scanned value through Android's active InputMethodService/InputConnection. The text field must therefore be focused in the remote PC application and AnyDesk must accept text/keyboard input from the phone. This is the same basic mechanism used by the existing QuickScan Keyboard concept.
