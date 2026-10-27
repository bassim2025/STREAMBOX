package com.streambox.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class BrowseActivity : Activity() {
    private data class Cat(val label: String, val count: Int, val kind: Int, val group: String = "")

    private var type = "live"
    private var items: List<Channel> = emptyList()
    private var cats: List<Cat> = emptyList()
    private var catIndex = 1
    private var shown: List<Channel> = emptyList()
    private var favs: Set<String> = emptySet()
    private var current: Channel? = null
    private var player: ExoPlayer? = null

    private lateinit var catList: ListView
    private lateinit var chList: ListView
    private lateinit var search: EditText
    private lateinit var nameView: TextView
    private lateinit var favBtn: TextView
    private lateinit var preview: PlayerView
    private val tabs = LinkedHashMap<String, TextView>()
    private val catAdapter = CatAdapter()
    private val chAdapter = ChAdapter()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        type = intent.getStringExtra("type") ?: "live"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2563EB.toInt(), 0xFF4C1D95.toInt(), 0xFFBE185D.toInt(), 0xFF1E1B4B.toInt())
            )
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF2563EB.toInt())
            setPadding(dp(12), 0, dp(12), 0)
        }
        for ((key, label) in listOf("home" to "Home", "live" to "Live", "movie" to "Movies", "series" to "Series")) {
            val t = TextView(this).apply {
                text = label; textSize = 16f; setTextColor(Color.WHITE)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                isFocusable = true; isClickable = true
                setOnClickListener { if (key == "home") finish() else switchType(key) }
            }
            tabs[key] = t
            bar.addView(t)
            bar.addView(View(this).apply { setBackgroundColor(0x99FFFFFF.toInt()) }, LinearLayout.LayoutParams(dp(1), dp(22)))
        }
        search = EditText(this).apply {
            hint = "Search"; setHintTextColor(0xCCFFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 14f
            setSingleLine(); setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); setStroke(dp(2), Color.WHITE); cornerRadius = dp(20).toFloat()
            }
        }
        bar.addView(search, LinearLayout.LayoutParams(dp(220), dp(38)).apply { setMargins(dp(12), 0, 0, 0) })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(TextView(this).apply {
            text = "StreamBox"; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        root.addView(bar, LinearLayout.LayoutParams(MATCH, dp(56)))

        catList = ListView(this).apply {
            divider = ColorDrawable(Color.TRANSPARENT); dividerHeight = dp(6)
            setPadding(dp(8), dp(10), dp(8), dp(8)); clipToPadding = false
            adapter = catAdapter
        }
        chList = ListView(this).apply {
            divider = ColorDrawable(0x22FFFFFF); dividerHeight = 1
            adapter = chAdapter
        }

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = PlayerView(this).apply {
            useController = false
            setBackgroundColor(0xFF1C1C21.toInt())
        }
        right.addView(preview, LinearLayout.LayoutParams(MATCH, 0, 1.1f))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        nameView = TextView(this).apply {
            text = "Channel Name"; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        }
        panel.addView(nameView, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val fullBtn = panelButton("Fullscreen") { openFull() }
        favBtn = panelButton("Add to Favorite") { toggleFav() }
        val findBtn = panelButton("Search") { search.requestFocus() }
        val btnRow = LinearLayout(this).apply { gravity = Gravity.END }
        for (v in listOf(fullBtn, favBtn, findBtn)) {
            btnRow.addView(v, LinearLayout.LayoutParams(WRAP, dp(40)).apply { setMargins(dp(8), 0, 0, 0) })
        }
        panel.addView(btnRow, LinearLayout.LayoutParams(MATCH, WRAP))
        right.addView(panel, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(catList, LinearLayout.LayoutParams(0, MATCH, 3f))
        body.addView(divider(), LinearLayout.LayoutParams(dp(1), MATCH))
        body.addView(chList, LinearLayout.LayoutParams(0, MATCH, 3.5f))
        body.addView(divider(), LinearLayout.LayoutParams(dp(1), MATCH))
        body.addView(right, LinearLayout.LayoutParams(0, MATCH, 4.5f))
        root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        catList.setOnItemClickListener { _, _, pos, _ ->
            catIndex = pos
            search.setText("")
            catAdapter.notifyDataSetChanged()
            filter()
        }
        chList.setOnItemClickListener { _, _, pos, _ ->
            val ch = shown[pos]
            if (current?.url == ch.url) openFull() else select(ch)
        }

        refreshTabs()
        load()
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build()
        player = p
        preview.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                nameView.text = (current?.name ?: "") + "\nCan't play this channel (" + error.errorCodeName + ")"
            }
        })
        current?.let { play(it) }
    }

    override fun onStop() {
        super.onStop()
        preview.player = null
        player?.release()
        player = null
    }

    private fun divider() = View(this).apply { setBackgroundColor(0xCCFFFFFF.toInt()) }

    private fun panelButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        isFocusable = true; isClickable = true
        background = stateBg(0xFF8FB3E8.toInt(), 0xFF8FB3E8.toInt(), 8, 0xFF3B6FD8.toInt())
        setOnClickListener { action() }
    }

    private fun refreshTabs() {
        for ((k, t) in tabs) {
            val sel = k == type
            t.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
            t.paintFlags = if (sel) t.paintFlags or Paint.UNDERLINE_TEXT_FLAG else t.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        }
    }

    private fun switchType(key: String) {
        type = key
        catIndex = 1
        current = null
        player?.stop()
        search.setText("")
        refreshTabs()
        load()
    }

    private fun buildCats() {
        favs = Store.favorites(this)
        val urls = items.map { it.url }.toSet()
        val groups = items.groupBy { it.group }
        val list = ArrayList<Cat>()
        list.add(Cat("Recently Viewed", Store.recents(this).count { it in urls }, 0))
        list.add(Cat("All", items.size, 1))
        list.add(Cat("Favorite", items.count { it.url in favs }, 2))
        for (g in groups.keys.sorted()) list.add(Cat(g, groups[g]?.size ?: 0, 3, g))
        cats = list
        if (catIndex >= cats.size) catIndex = 1
        catAdapter.notifyDataSetChanged()
    }

    private fun load() {
        items = Store.visible(this, type)
        buildCats()
        nameView.text = if (items.isEmpty()) "Nothing here yet. Add playlists on the website, then tap Reload on the main menu." else "Channel Name"
        updateFavBtn()
        filter()
    }

    private fun filter() {
        favs = Store.favorites(this)
        val q = search.text.toString().trim().lowercase()
        val base: List<Channel> = if (q.isNotEmpty()) items else {
            val c = cats[catIndex]
            when (c.kind) {
                0 -> { val m = items.associateBy { it.url }; Store.recents(this).mapNotNull { m[it] } }
                1 -> items
                2 -> items.filter { it.url in favs }
                else -> items.filter { it.group == c.group }
            }
        }
        shown = base.filter { q.isEmpty() || it.name.lowercase().contains(q) }.take(2000)
        chAdapter.notifyDataSetChanged()
    }

    private fun select(ch: Channel) {
        current = ch
        nameView.text = ch.name
        Store.addRecent(this, ch.url)
        buildCats()
        updateFavBtn()
        chAdapter.notifyDataSetChanged()
        play(ch)
    }

    private fun play(ch: Channel) {
        val p = player ?: return
        p.setMediaItem(MediaItem.fromUri(ch.url))
        p.prepare()
        p.playWhenReady = true
    }

    private fun openFull() {
        val ch = current ?: return
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("url", ch.url).putExtra("name", ch.name))
    }

    private fun toggleFav() {
        val ch = current ?: return
        Store.toggleFav(this, ch.url)
        buildCats()
        updateFavBtn()
        filter()
    }

    private fun updateFavBtn() {
        val isFav = current?.let { it.url in Store.favorites(this) } ?: false
        favBtn.text = if (isFav) "Remove Favorite" else "Add to Favorite"
    }

    private inner class CatAdapter : BaseAdapter() {
        override fun getCount() = cats.size
        override fun getItem(p: Int): Any = cats[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val c = cats[p]
            val row = LinearLayout(this@BrowseActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setBackgroundColor(if (p == catIndex) 0xFF3B6FD8.toInt() else 0xCC93B4E6.toInt())
            }
            row.addView(TextView(this@BrowseActivity).apply {
                text = c.label; setTextColor(Color.WHITE); textSize = 15f; maxLines = 1
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(TextView(this@BrowseActivity).apply {
                text = c.count.toString(); setTextColor(Color.WHITE); textSize = 15f
            })
            return row
        }
    }

    private inner class ChAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int): Any = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val t = (v as? TextView) ?: TextView(this@BrowseActivity)
            val ch = shown[p]
            t.text = (if (ch.url in favs) "★ " else "") + ch.name
            t.setTextColor(Color.WHITE)
            t.textSize = 15f
            t.maxLines = 1
            t.setPadding(dp(14), dp(12), dp(14), dp(12))
            t.setBackgroundColor(if (current?.url == ch.url) 0x55FFFFFF else Color.TRANSPARENT)
            return t
        }
    }
}
