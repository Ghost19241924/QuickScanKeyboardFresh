package com.parshwnath.quickscankeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection

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
        return view
    }

    private fun openScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    fun sendScanResult(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val ic: InputConnection = currentInputConnection ?: return
        ic.commitText(clean, 1)
        sendEnter()
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
    }
}
