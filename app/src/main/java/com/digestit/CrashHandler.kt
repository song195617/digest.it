package com.digestit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

class CrashHandler(private val app: DigestItApplication) : Thread.UncaughtExceptionHandler {

    private val default = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        val trace = buildString {
            appendLine("=== CRASH ===")
            appendLine("Thread: ${t.name}")
            appendLine()
            appendLine(e.stackTraceToString())
        }
        // Always write to file first — visible even if activity can't start
        try {
            File(app.filesDir, "last_crash.txt").writeText(trace)
        } catch (_: Exception) {}

        // Try to show crash screen
        try {
            val intent = Intent(app, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("trace", trace)
            }
            app.startActivity(intent)
        } catch (_: Exception) {
            default?.uncaughtException(t, e)
        }
    }
}

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show from intent, or read saved file if activity was restarted
        val trace = intent.getStringExtra("trace")
            ?: runCatching { File(filesDir, "last_crash.txt").readText() }.getOrElse { "No crash info" }
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = trace
            textSize = 11f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
        }
        scroll.addView(tv)
        setContentView(scroll)
    }
}
