package com.streambox.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this)
        view.setBackgroundColor(Color.BLACK)
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build()
        player = p
        view.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Playback error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
            }
        })
        p.setMediaItem(MediaItem.fromUri(intent.getStringExtra("url") ?: return))
        p.prepare()
        p.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        view.player = null
        player?.release()
        player = null
    }
}
