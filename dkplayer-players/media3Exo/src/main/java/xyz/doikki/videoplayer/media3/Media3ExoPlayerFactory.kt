package xyz.doikki.videoplayer.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import xyz.doikki.videoplayer.player.PlayerFactory

/**
 * Media3 播放器工厂，用于创建 [Media3ExoPlayer] 实例。
 */
class Media3ExoPlayerFactory : PlayerFactory<Media3ExoPlayer>() {

    @OptIn(UnstableApi::class)
    override fun createPlayer(context: Context): Media3ExoPlayer {
        return Media3ExoPlayer(context)
    }

    companion object {
        @JvmStatic
        fun create(): Media3ExoPlayerFactory = Media3ExoPlayerFactory()
    }
}
