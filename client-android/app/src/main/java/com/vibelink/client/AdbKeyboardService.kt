package com.vibelink.client

import android.inputmethodservice.InputMethodService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Base64
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.View

class AdbKeyboardService : InputMethodService() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connection = currentInputConnection ?: return
            when (intent.action) {
                ACTION_TEXT -> {
                    intent.getStringExtra(EXTRA_TEXT)?.let { connection.commitText(it, 1) }
                }
                ACTION_B64 -> {
                    intent.getStringExtra(EXTRA_TEXT)
                        ?.let { Base64.decode(it, Base64.DEFAULT) }
                        ?.toString(Charsets.UTF_8)
                        ?.let { connection.commitText(it, 1) }
                }
                ACTION_CLEAR -> clearText(connection)
                ACTION_ENTER -> connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ACTION_DELETE -> connection.deleteSurroundingText(1, 0)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(ACTION_TEXT)
            addAction(ACTION_B64)
            addAction(ACTION_CLEAR)
            addAction(ACTION_ENTER)
            addAction(ACTION_DELETE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateInputView(): View? = null

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
    }

    private fun clearText(connection: InputConnection) {
        connection.performContextMenuAction(android.R.id.selectAll)
        connection.commitText("", 1)
    }

    companion object {
        const val ACTION_TEXT = "com.vibelink.client.ADB_INPUT_TEXT"
        const val ACTION_B64 = "com.vibelink.client.ADB_INPUT_B64"
        const val ACTION_CLEAR = "com.vibelink.client.ADB_CLEAR_TEXT"
        const val ACTION_ENTER = "com.vibelink.client.ADB_ENTER"
        const val ACTION_DELETE = "com.vibelink.client.ADB_DELETE"
        const val EXTRA_TEXT = "text"
    }
}
