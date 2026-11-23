package com.focushours.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Android tells this service whenever a new app window comes to the front.
 * If it's a locked app and we're outside the allowed hours, we cover it with the lock screen.
 */
class AppBlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundPkg: String? = null

    // Re-check every 20 seconds, so an app that's open when the allowed window ends gets locked too.
    private val recheck = object : Runnable {
        override fun run() {
            foregroundPkg?.let { checkAndBlock(it) }
            handler.postDelayed(this, 20_000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, 20_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return              // our own screens
        if (pkg in IGNORED) return                  // keyboard, system bars etc. don't change the "current app"
        foregroundPkg = pkg
        checkAndBlock(pkg)
    }

    private fun checkAndBlock(pkg: String) {
        val prefs = Prefs(this)
        if (pkg !in prefs.lockedApps) return
        if (prefs.isAllowedNow() || prefs.hasPass(pkg)) return
        val intent = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        super.onDestroy()
    }

    companion object {
        private val IGNORED = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey"
        )
    }
}
