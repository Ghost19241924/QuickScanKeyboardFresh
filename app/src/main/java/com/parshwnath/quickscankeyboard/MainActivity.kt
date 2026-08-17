package com.parshwnath.quickscankeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.enableButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.testScanButton).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        findViewById<TextView>(R.id.statusText).text = if (enabled) {
            "Keyboard is enabled. Select QuickScan Keyboard in the AnyDesk text-input field, then use SCAN QR."
        } else {
            "First enable QuickScan Keyboard in Android keyboard settings."
        }
    }
}
