package com.focushours.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen "this app is locked" screen. */
class BlockActivity : Activity() {

    private var pkg: String = ""
    private var timer: CountDownTimer? = null
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var passBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0E2238"))
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        icon = ImageView(this)
        root.addView(icon, LinearLayout.LayoutParams(dp(84), dp(84)).apply { bottomMargin = dp(24) })

        title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 26f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        }
        root.addView(title)

        subtitle = TextView(this).apply {
            setTextColor(Color.parseColor("#B8C4D2")); textSize = 17f; gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(36))
        }
        root.addView(subtitle)

        val homeBtn = pillButton("Go back to home screen", "#2F6BFF", Color.WHITE) { goHome() }
        root.addView(homeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        passBtn = pillButton("", "#00000000", Color.parseColor("#B8C4D2")) { startPassCountdown() }
        root.addView(passBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })

        setContentView(root)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(i: Intent?) {
        pkg = i?.getStringExtra(EXTRA_PKG) ?: ""
        timer?.cancel()
        val prefs = Prefs(this)
        val pm = packageManager
        val name = try {
            val info = pm.getApplicationInfo(pkg, 0)
            icon.setImageDrawable(pm.getApplicationIcon(info))
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) { "This app" }

        title.text = "$name is locked right now"
        val next = prefs.nextOpening()
        subtitle.text = if (next != null) "It opens again at $next.\nUse this time for something that matters."
                        else "You haven't set any allowed hours yet."
        updatePassButton()
    }

    private fun updatePassButton() {
        val left = Prefs(this).passesLeftToday()
        passBtn.isEnabled = left > 0
        passBtn.text = if (left > 0) "I really need it: ${Prefs.PASS_MINUTES} minutes ($left left today)"
                       else "No emergency passes left today"
    }

    /** A 15-second wait before the pass is granted, to break the habit of opening the app on autopilot. */
    private fun startPassCountdown() {
        passBtn.isEnabled = false
        timer?.cancel()
        timer = object : CountDownTimer(15_000L, 1_000L) {
            override fun onTick(ms: Long) { passBtn.text = "Wait ${(ms / 1000) + 1} s… still want it?" }
            override fun onFinish() {
                if (Prefs(this@BlockActivity).usePass(pkg)) {
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    finish()
                } else updatePassButton()
            }
        }.start()
    }

    private fun goHome() {
        timer?.cancel()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Deprecated("Back always goes home, never back into the locked app")
    override fun onBackPressed() = goHome()

    override fun onDestroy() { timer?.cancel(); super.onDestroy() }

    private fun pillButton(text: String, bg: String, fg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(fg)
        background = GradientDrawable().apply {
            cornerRadius = 100f
            setColor(Color.parseColor(bg))
            setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#3A4D66"))
        }
        setOnClickListener { onClick() }
    }

    companion object { const val EXTRA_PKG = "pkg" }
}
