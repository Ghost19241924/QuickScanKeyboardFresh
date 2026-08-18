package com.parshwnath.quickscankeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
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

    /**
     * Switches back to whatever keyboard (Gboard etc.) was active
     * before QuickScan Keyboard, so the user can type normally again.
     *
     * On Android 9+ this is a direct one-tap switch. On older versions
     * (down to our minSdk 23) that API doesn't exist, so we fall back
     * to the system's keyboard-picker dialog instead.
     */
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
        typeText(clean)
        sendEnter()
    }

    /**
     * Types text as real synthetic key-press events instead of
     * InputConnection.commitText().
     *
     * Why: commitText() only works when the focused field is a genuine
     * local Android EditText that this IME is connected to. AnyDesk's
     * remote-desktop field isn't that — it's a streamed picture of the
     * Windows screen, and AnyDesk forwards raw keystrokes to it, the
     * same way a physical keyboard would. commitText() has nothing to
     * write into there, so it silently does nothing. Sending actual
     * KeyEvents (exactly like sendEnter() already does for Enter) is
     * what actually reaches the remote PC.
     */
    private fun typeText(text: String) {
        val ic: InputConnection = currentInputConnection ?: return
        val keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyCharacterMap.getEvents(text.toCharArray())
        if (events != null) {
            for (event in events) {
                ic.sendKeyEvent(event)
            }
        } else {
            // Rare fallback: a character couldn't be mapped to a key event.
            // Better to still deliver something than silently drop it.
            ic.commitText(text, 1)
        }
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }
}
