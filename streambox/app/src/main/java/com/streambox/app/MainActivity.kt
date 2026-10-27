package com.streambox.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var playlistInfo: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF09090B.toInt())
            setPadding(dp(24), dp(10), dp(24), dp(10))
        }

        val top = FrameLayout(this)
        val logoText = SpannableString("StreamBox")
        logoText.setSpan(ForegroundColorSpan(0xFFEF4444.toInt()), 6, 9, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val logo = TextView(this).apply {
            text = logoText; textSize = 28f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }
        top.addView(logo, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        playlistInfo = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f }
        top.addView(playlistInfo, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(top, LinearLayout.LayoutParams(MATCH, dp(56)))

        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val live = menuTile(R.drawable.ic_tv, "Live", 72)
        live.setOnClickListener { openBrowse("live") }
        grid.addView(live, LinearLayout.LayoutParams(0, MATCH, 3f).apply { setMargins(0, 0, dp(8), 0) })

        val movies = menuTile(R.drawable.ic_play, "Movies", 40)
        movies.setOnClickListener { openBrowse("movie") }
        val series = menuTile(R.drawable.ic_series, "Series", 40)
        series.setOnClickListener { openBrowse("series") }
        val account = menuTile(R.drawable.ic_account, "Account", 40)
        account.setOnClickListener { accountDialog() }
        val change = menuTile(R.drawable.ic_swap, "Change Playlist", 40)
        change.setOnClickListener { playlistDialog() }

        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(this)
        val r2 = LinearLayout(this)
        r1.addView(movies, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(0, 0, dp(8), dp(8)) })
        r1.addView(series, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(0, 0, 0, dp(8)) })
        r2.addView(account, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(0, 0, dp(8), 0) })
        r2.addView(change, LinearLayout.LayoutParams(0, MATCH, 1f))
        mid.addView(r1, LinearLayout.LayoutParams(MATCH, 0, 1f))
        mid.addView(r2, LinearLayout.LayoutParams(MATCH, 0, 1f))
        grid.addView(mid, LinearLayout.LayoutParams(0, MATCH, 4f).apply { setMargins(0, 0, dp(8), 0) })

        val settings = sideButton(R.drawable.ic_settings, "Settings")
        settings.setOnClickListener { serverDialog() }
        val reload = sideButton(R.drawable.ic_reload, "Reload")
        reload.setOnClickListener { sync() }
        val exit = sideButton(R.drawable.ic_exit, "EXIT")
        exit.setOnClickListener { finish() }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for ((i, v) in listOf(settings, reload, exit).withIndex()) {
            right.addView(v, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, 0, 0, if (i < 2) dp(8) else 0) })
        }
        grid.addView(right, LinearLayout.LayoutParams(0, MATCH, 3.2f))
        root.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val version = TextView(this).apply {
            text = "v1.0"; setTextColor(0xFFCBD5E1.toInt()); textSize = 14f; gravity = Gravity.END
        }
        root.addView(version, LinearLayout.LayoutParams(MATCH, WRAP))

        setContentView(root)
        live.requestFocus()
        updateTop()
        if (Store.server(this).isBlank()) serverDialog() else if (Store.load(this).isEmpty()) sync()
    }

    override fun onResume() { super.onResume(); updateTop() }

    private fun updateTop() {
        playlistInfo.text = "Current playlist: " + Store.active(this).ifEmpty { "All playlists" }
    }

    private fun menuTile(icon: Int, label: String, iconDp: Int): LinearLayout {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = stateBg(0xFF4A0E12.toInt(), 0xFF8B1E26.toInt(), 14, 0xFF6B1A21.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        t.addView(ImageView(this).apply { setImageResource(icon) }, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
        t.addView(TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 14f
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
        })
        return t
    }

    private fun sideButton(icon: Int, label: String): LinearLayout {
        val t = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = stateBg(0xFF140909.toInt(), 0xFF8B1E26.toInt(), 12, 0xFF6B1A21.toInt())
            setPadding(dp(20), 0, dp(12), 0)
        }
        t.addView(ImageView(this).apply { setImageResource(icon) }, LinearLayout.LayoutParams(dp(28), dp(28)))
        t.addView(TextView(this).apply {
            text = label; setTextColor(Color.WHITE); textSize = 15f; setPadding(dp(16), 0, 0, 0)
        })
        return t
    }

    private fun openBrowse(type: String) {
        startActivity(Intent(this, BrowseActivity::class.java).putExtra("type", type))
    }

    private fun sync() {
        Toast.makeText(this, "Reloading…", Toast.LENGTH_SHORT).show()
        thread {
            val msg = try { Portal.sync(this) } catch (e: Exception) { "Reload failed: ${e.message}" }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); updateTop() }
        }
    }

    private fun accountDialog() {
        val s = Store.server(this).ifBlank { "(not set)" }
        AlertDialog.Builder(this)
            .setTitle("Account")
            .setMessage("MAC address\n${Store.mac(this)}\n\nDevice key\n${Store.key(this)}\n\nPortal\n$s")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun playlistDialog() {
        val names = Store.playlists(this)
        if (names.isEmpty()) {
            Toast.makeText(this, "No playlists yet. Add one on the website, then tap Reload.", Toast.LENGTH_LONG).show()
            return
        }
        val options = listOf("All playlists") + names
        AlertDialog.Builder(this)
            .setTitle("Change playlist")
            .setItems(options.toTypedArray()) { _, which ->
                Store.setActive(this, if (which == 0) "" else options[which])
                updateTop()
            }
            .show()
    }

    private fun serverDialog() {
        val input = EditText(this).apply {
            setText(Store.server(this@MainActivity).ifBlank { "https://" })
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Portal address")
            .setMessage("Address of your playlist portal, e.g. https://iptv.example.com")
            .setView(input)
            .setPositiveButton("Save") { _, _ -> Store.setServer(this, input.text.toString()); updateTop(); sync() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
