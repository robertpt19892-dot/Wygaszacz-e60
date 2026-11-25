package com.example.bmwe60cockpit

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var manager: CockpitManager? = null
    private var pendingSlot = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        manager = CockpitManager(this, isScreensaver = false) { slot ->
            pendingSlot = slot
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Wybierz zdjęcie tła"), 2001)
        }
        setContentView(manager!!.rootLayout)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> manager?.savePhoto(pendingSlot, uri) }
        }
    }

    override fun onDestroy() {
        manager?.destroy()
        super.onDestroy()
    }
}

class E60DreamService : DreamService() {
    private var manager: CockpitManager? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        manager = CockpitManager(this, isScreensaver = true)
        setContentView(manager!!.rootLayout)
    }

    override fun onDetachedFromWindow() {
        manager?.destroy()
        super.onDetachedFromWindow()
    }
}
