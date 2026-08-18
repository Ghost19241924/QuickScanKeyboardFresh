package com.parshwnath.quickscankeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class QuickScanKeyboardService : InputMethodService() {
    companion object {
        @Volatile
        var current: QuickScanKeyboardService? = null
    }

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        view.findViewById<View>(R.id.scanButton).setOnClickListener {
            openScanner()
        }
        view.findViewById<View>(R.id.enterButton).setOnClickListener {
            sendEnter()
        }
        view.findViewById<View>(R.id.switchKeyboardButton).setOnClickListener {
            switchToPreviousKeyboard()
        }
        return view
    }

    private fun switchToPreviousKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun sendScanResult(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val ic = currentInputConnection ?: return

        // Send digits as raw hardware key events for AnyDesk
        sendHardwareString(ic, clean)

        // Wait 200ms for AnyDesk buffer to register characters, then send ENTER
        Handler(Looper.getMainLooper()).postDelayed({
            sendEnter()
        }, 200)
    }

    private fun sendHardwareString(ic: InputConnection, text: String) {
        for (char in text) {
            val keyCode = when (char) {
                '0' -> KeyEvent.KEYCODE_0
                '1' -> KeyEvent.KEYCODE_1
                '2' -> KeyEvent.KEYCODE_2
                '3' -> KeyEvent.KEYCODE_3
                '4' -> KeyEvent.KEYCODE_4
                '5' -> KeyEvent.KEYCODE_5
                '6' -> KeyEvent.KEYCODE_6
                '7' -> KeyEvent.KEYCODE_7
                '8' -> KeyEvent.KEYCODE_8
                '9' -> KeyEvent.KEYCODE_9
                else -> -1
            }

            if (keyCode != -1) {
                val time = System.currentTimeMillis()
                ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0))
                ic.sendKeyEvent(KeyEvent(time, time + 5, KeyEvent.ACTION_UP, keyCode, 0))
            } else {
                // Fallback for non-numeric characters
                ic.commitText(char.toString(), 1)
            }
        }
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        ic.sendKeyEvent(KeyEvent(time, time + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }
}
