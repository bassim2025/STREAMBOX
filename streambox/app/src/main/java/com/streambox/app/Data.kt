package com.streambox.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

data class Channel(val name: String, val group: String, val url: String, val type: String, val playlist: String = "")

object Store {
    private var mem: List<Channel>? = null
    private fun prefs(c: Context) = c.getSharedPreferences("sb", Context.MODE_PRIVATE)

    // Android blocks the real MAC, so the app creates its own stable ID in MAC format.
    fun mac(c: Context): String {
        val p = prefs(c)
        p.getString("mac", null)?.let { return it }
        val b = ByteArray(6); SecureRandom().nextBytes(b)
        b[0] = ((b[0].toInt() or 0x02) and 0xFE).toByte()
        val m = b.joinToString(":") { "%02X".format(it) }
        p.edit().putString("mac", m).apply()
        return m
    }
    fun key(c: Context): String {
        val p = prefs(c)
        p.getString("key", null)?.let { return it }
        val r = SecureRandom()
        val k = (1..8).joinToString("") { r.nextInt(10).toString() }
        p.edit().putString("key", k).apply()
        return k
    }
    fun server(c: Context): String = prefs(c).getString("server", "") ?: ""
    fun setServer(c: Context, s: String) = prefs(c).edit().putString("server", s.trim().trimEnd('/')).apply()

    fun active(c: Context): String = prefs(c).getString("active", "") ?: ""
    fun setActive(c: Context, v: String) = prefs(c).edit().putString("active", v).apply()
    fun playlists(c: Context): List<String> = load(c).map { it.playlist }.filter { it.isNotBlank() }.distinct()

    fun favorites(c: Context): Set<String> = HashSet(prefs(c).getStringSet("fav", emptySet()) ?: emptySet<String>())
    fun toggleFav(c: Context, url: String): Boolean {
        val s = favorites(c).toMutableSet()
        val added = s.add(url)
        if (!added) s.remove(url)
        prefs(c).edit().putStringSet("fav", s).apply()
        return added
    }
    fun recents(c: Context): List<String> =
        (prefs(c).getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun addRecent(c: Context, url: String) {
        val l = recents(c).toMutableList()
        l.remove(url); l.add(0, url)
        prefs(c).edit().putString("recent", l.take(30).joinToString("\n")).apply()
    }

    fun save(c: Context, list: List<Channel>) {
        mem = list
        c.openFileOutput("channels.tsv", Context.MODE_PRIVATE).bufferedWriter().use { w ->
            for (ch in list) {
                w.write(listOf(ch.type, ch.group, ch.name, ch.url, ch.playlist)
                    .joinToString("\t") { it.replace("\t", " ").replace("\n", " ") })
                w.newLine()
            }
        }
    }
    fun load(c: Context): List<Channel> {
        mem?.let { return it }
        val out = ArrayList<Channel>()
        try {
            c.openFileInput("channels.tsv").bufferedReader().useLines { lines ->
                for (l in lines) {
                    val p = l.split("\t")
                    if (p.size >= 4) out.add(Channel(p[2], p[1], p[3], p[0], p.getOrElse(4) { "" }))
                }
            }
        } catch (_: Exception) {}
        mem = out
        return out
    }
    /** Channels of one type, limited to the active playlist (or all playlists). */
    fun visible(c: Context, type: String): List<Channel> {
        val a = active(c)
        return load(c).filter { it.type == type && (a.isEmpty() || it.playlist == a) }
    }
}

object M3U {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var group = "Uncategorized"
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.startsWith("#EXTINF")) {
                val a = attr.findAll(l).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                group = a["group-title"]?.ifBlank { null } ?: "Uncategorized"
                name = l.substringAfterLast(",", "").trim().ifEmpty { a["tvg-name"] ?: "" }
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                val low = l.lowercase()
                val type = when {
                    "/movie/" in low -> "movie"
                    "/series/" in low -> "series"
                    else -> "live"
                }
                out.add(Channel(name.ifEmpty { l }, group, l, type))
                name = ""
                group = "Uncategorized"
            }
        }
        return out
    }
}

object Net {
    fun get(u: String, timeout: Int = 30000): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (c.responseCode >= 400) throw Exception("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
    fun post(u: String, json: String) {
        val c = URL(u).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 20000; c.readTimeout = 20000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        if (c.responseCode >= 400) throw Exception("Server rejected this device (${c.responseCode})")
    }
}

object Portal {
    /** Registers this device, downloads every playlist added on the website and stores the channels. */
    fun sync(c: Context): String {
        val s = Store.server(c)
        if (s.isBlank()) throw Exception("Set the server address in Settings")
        val mac = Store.mac(c)
        val key = Store.key(c)
        Net.post("$s/api/register", JSONObject().put("mac", mac).put("key", key).toString())
        val arr = JSONArray(Net.get("$s/api/playlists?mac=${URLEncoder.encode(mac, "UTF-8")}&key=$key"))
        val all = ArrayList<Channel>()
        var failed = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var u = o.getString("url")
            if (u.startsWith("/")) u = s + u
            val pname = o.optString("name", "Playlist")
            try { all += M3U.parse(Net.get(u, 120000)).map { it.copy(playlist = pname) } } catch (_: Exception) { failed++ }
        }
        Store.save(c, all)
        if (Store.active(c).isNotEmpty() && Store.active(c) !in Store.playlists(c)) Store.setActive(c, "")
        return "Loaded ${all.size} entries from ${arr.length() - failed} playlist(s)" +
            if (failed > 0) " ($failed failed)" else ""
    }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Rounded background with a white outline and a different fill when focused (TV remote) or pressed. */
fun Context.stateBg(fill: Int, stroke: Int, radiusDp: Int, focusFill: Int): StateListDrawable {
    fun shape(f: Int, s: Int, w: Int) = GradientDrawable().apply {
        setColor(f); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(w), s)
    }
    val hi = shape(focusFill, 0xFFFFFFFF.toInt(), 3)
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), hi)
        addState(intArrayOf(android.R.attr.state_pressed), hi)
        addState(intArrayOf(), shape(fill, stroke, 2))
    }
}
