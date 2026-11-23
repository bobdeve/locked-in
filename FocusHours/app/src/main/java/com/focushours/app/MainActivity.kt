package com.focushours.app

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    data class AppItem(val pkg: String, val name: String, val icon: Drawable)

    private lateinit var prefs: Prefs
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var enableBtn: Button
    private lateinit var windowsBox: LinearLayout
    private lateinit var countText: TextView
    private val adapter = AppAdapter()
    private var allApps: List<AppItem> = emptyList()
    private var query = ""

    private val navy = Color.parseColor("#0E2238")
    private val blue = Color.parseColor("#2F6BFF")
    private val grey = Color.parseColor("#5B6573")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- Header / status ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(dp(20), dp(22), dp(20), dp(18))
        }
        header.addView(TextView(this).apply {
            text = "Focus Hours"; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        })
        statusTitle = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; setPadding(0, dp(10), 0, 0) }
        statusText = TextView(this).apply { setTextColor(Color.parseColor("#B8C4D2")); textSize = 14f }
        header.addView(statusTitle); header.addView(statusText)
        enableBtn = pill("Turn on app lock", blue, Color.WHITE) { openAccessibilitySettings() }
        header.addView(enableBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { topMargin = dp(12) })
        root.addView(header)

        // ---- Allowed hours ----
        val hours = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(4)) }
        hours.addView(sectionTitle("Allowed hours"))
        hours.addView(TextView(this).apply {
            text = "Locked apps only open during these times."; setTextColor(grey); textSize = 13f
        })
        windowsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        val scroller = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(windowsBox) }
        hours.addView(scroller)
        root.addView(hours)

        // ---- App list ----
        val appsHead = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(6)) }
        appsHead.addView(sectionTitle("Apps to lock"))
        countText = TextView(this).apply { setTextColor(grey); textSize = 13f }
        appsHead.addView(countText)
        val search = EditText(this).apply {
            hint = "Search apps"; textSize = 16f; setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s.toString().trim(); adapter.refresh() }
            })
        }
        appsHead.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(appsHead)

        val list = ListView(this).apply { this.adapter = this@MainActivity.adapter; divider = null }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        renderWindows()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    // ---------- Status ----------

    private fun isLockServiceOn(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val mine = ComponentName(this, AppBlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun renderStatus() {
        val on = isLockServiceOn()
        enableBtn.visibility = if (on) View.GONE else View.VISIBLE
        if (!on) {
            statusTitle.text = "App lock is OFF"
            statusText.text = "Tap below, find \"Focus Hours\" under installed apps and turn it on."
            return
        }
        val end = prefs.currentWindowEnd()
        if (end != null) {
            statusTitle.text = "Open time now"
            statusText.text = "Your locked apps are open until $end."
        } else {
            statusTitle.text = "Focus time"
            val next = prefs.nextOpening()
            statusText.text = if (next != null) "Locked apps open again at $next." else "Add some allowed hours below."
        }
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Turn on app lock")
            .setMessage("Focus Hours uses Android's accessibility feature only to notice which app you open. It doesn't read your screen, messages or passwords, and nothing leaves your phone.\n\nOn the next screen, tap \"Focus Hours\" (sometimes under \"Installed apps\" or \"Downloaded apps\") and switch it on.\n\nIf the switch is greyed out: open Settings → Apps → Focus Hours → ⋮ menu → \"Allow restricted settings\", then try again.")
            .setPositiveButton("Open settings") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Allowed hours ----------

    private fun renderWindows() {
        windowsBox.removeAllViews()
        for (w in prefs.windows) {
            val chip = pill("${w.label()}  ✕", Color.parseColor("#E8EEFF"), navy) {
                AlertDialog.Builder(this)
                    .setMessage("Remove ${w.label()}?")
                    .setPositiveButton("Remove") { _, _ -> prefs.windows = prefs.windows - w; renderWindows(); renderStatus() }
                    .setNegativeButton("Cancel", null).show()
            }
            windowsBox.addView(chip, chipParams())
        }
        windowsBox.addView(pill("+ Add time", Color.WHITE, blue) { addWindow() }, chipParams())
    }

    private fun addWindow() {
        val is24 = DateFormat.is24HourFormat(this)
        TimePickerDialog(this, { _, sh, sm ->
            TimePickerDialog(this, { _, eh, em ->
                val start = sh * 60 + sm; val end = eh * 60 + em
                if (start != end) {
                    prefs.windows = prefs.windows + TimeWindow(start, end)
                    renderWindows(); renderStatus()
                }
            }, (sh + 1) % 24, sm, is24).apply { setTitle("Until…") }.show()
        }, 18, 0, is24).apply { setTitle("Allowed from…") }.show()
    }

    // ---------- App list ----------

    private fun loadApps() {
        countText.text = "Loading your apps…"
        Executors.newSingleThreadExecutor().execute {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
                .sortedBy { it.name.lowercase() }
            runOnUiThread { allApps = apps; adapter.refresh() }
        }
    }

    inner class AppAdapter : BaseAdapter() {
        private var shown: List<AppItem> = emptyList()

        fun refresh() {
            val locked = prefs.lockedApps
            // Locked apps first, then the rest alphabetically
            shown = allApps
                .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                .sortedWith(compareBy<AppItem> { it.pkg !in locked }.thenBy { it.name.lowercase() })
            countText.text = "${locked.size} locked · ${allApps.size} apps on this phone"
            notifyDataSetChanged()
        }

        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(8), dp(12), dp(8))
                addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(TextView(context).apply { textSize = 16f; setPadding(dp(14), 0, dp(8), 0); setTextColor(navy) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(CheckBox(context).apply { isClickable = false; isFocusable = false })
            }
            val item = shown[position]
            val locked = item.pkg in prefs.lockedApps
            (row.getChildAt(0) as ImageView).setImageDrawable(item.icon)
            (row.getChildAt(1) as TextView).text = item.name
            (row.getChildAt(2) as CheckBox).isChecked = locked
            row.setOnClickListener {
                prefs.lockedApps = if (locked) prefs.lockedApps - item.pkg else prefs.lockedApps + item.pkg
                refresh()
            }
            return row
        }
    }

    // ---------- Small UI helpers ----------

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(navy)
    }

    private fun chipParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { rightMargin = dp(8) }

    private fun pill(text: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 14f; setTextColor(fg)
        minHeight = 0; minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)
        stateListAnimator = null
        background = GradientDrawable().apply {
            cornerRadius = 100f; setColor(bg); setStroke(dp(1), Color.parseColor("#C9D4EA"))
        }
        setOnClickListener { onClick() }
    }
}
