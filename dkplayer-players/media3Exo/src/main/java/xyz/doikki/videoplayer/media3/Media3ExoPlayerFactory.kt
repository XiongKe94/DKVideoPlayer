package xyz.doikki.videoplayer.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import xyz.doikki.videoplayer.player.PlayerFactory

class Media3ExoPlayerFactory : PlayerFactory<Media3ExoPlayer>() {

    var cacheConfig: CacheConfig? = null
        private set

    @OptIn(UnstableApi::class)
    override fun createPlayer(context: Context): Media3ExoPlayer {
        return Media3ExoPlayer(context, cacheConfig)
    }

    fun setCacheConfig(config: CacheConfig?): Media3ExoPlayerFactory {
        cacheConfig = config
        return this
    }

    companion object {
        @JvmStatic
        fun create(): Media3ExoPlayerFactory = Media3ExoPlayerFactory()
    }
}
