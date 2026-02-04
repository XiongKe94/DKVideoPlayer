package xyz.doikki.videoplayer.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * Media3 播放器工厂，用于创建 [MediaPlayer] 实例。
 */
class MediaPlayerFactory : PlayerFactory<MediaPlayer>() {

    @OptIn(UnstableApi::class) override fun createPlayer(context: Context): MediaPlayer {
        return MediaPlayer(context)
    }

    companion object {
        @JvmStatic
        fun create(): MediaPlayerFactory = MediaPlayerFactory()
    }
}
