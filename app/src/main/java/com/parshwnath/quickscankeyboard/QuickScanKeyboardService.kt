package com.parshwnath.quickscankeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
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
        if (currentInputConnection == null) return

        // 1. Commit text first to guarantee string injection into AnyDesk
        currentInputConnection?.commitText(clean, 1)

        // 2. Type text key events for physical keyboard emulation
        typeText(clean)

        // 3. Post a short delay (150ms) before sending Enter so AnyDesk can sync the buffer
        Handler(Looper.getMainLooper()).postDelayed({
            sendEnter()
        }, 150)
    }

    private fun typeText(text: String) {
        val ic: InputConnection = currentInputConnection ?: return
        val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyCharacterMap.getEvents(text.toCharArray())
        if (events != null) {
            for (event in events) {
                ic.sendKeyEvent(event)
            }
        }
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()
        
        // Key Down
        ic.sendKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0)
        )
        // Key Up
        ic.sendKeyEvent(
            KeyEvent(eventTime, System.currentTimeMillis() + 10, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0)
        )
    }
}
