package xyz.doikki.videoplayer.media3

import Media3ExoSourceHelperImpl
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.util.EventLogger
import xyz.doikki.videoplayer.player.AbstractPlayer
import xyz.doikki.videoplayer.player.VideoViewManager

/**
 * 基于 AndroidX Media3 ExoPlayer 的播放器实现。
 */
@UnstableApi
class Media3ExoPlayer(context: Context, private val cacheConfig: CacheConfig? = null) : AbstractPlayer(), Player.Listener {

    protected val appContext: Context = context.applicationContext
    protected var mediaSourceHelper: Media3ExoSourceHelperImpl = Media3ExoSourceHelper.getInstance(context)
    protected var internalPlayer: ExoPlayer? = null
    var mediaSource: MediaSource? = null
        protected set

    private var speedPlaybackParameters: PlaybackParameters? = null
    private var isPreparing: Boolean = false
    private var loadControl: LoadControl? = null
    private var renderersFactory: RenderersFactory? = null
    private var trackSelector: TrackSelector? = null

    override fun initPlayer() {
        val rFactory = renderersFactory ?: DefaultRenderersFactory(appContext).also { renderersFactory = it }
        val tSelector = trackSelector ?: DefaultTrackSelector(appContext).also { trackSelector = it }
        val lControl = loadControl ?: DefaultLoadControl().also { loadControl = it }
        internalPlayer = ExoPlayer.Builder(appContext)
            .setRenderersFactory(rFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext))
            .setTrackSelector(tSelector)
            .setLoadControl(lControl)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(appContext))
            .setAnalyticsCollector(DefaultAnalyticsCollector(Clock.DEFAULT))
            .build()
            .also { player ->
                if (VideoViewManager.getConfig().mIsEnableLog && tSelector is MappingTrackSelector) {
                    player.addAnalyticsListener(EventLogger(tSelector, "Media3"))
                }
                player.addListener(this)
            }
        setOptions()
    }

    fun setTrackSelector(selector: TrackSelector?) {
        trackSelector = selector
    }

    fun setRenderersFactory(factory: RenderersFactory?) {
        renderersFactory = factory
    }

    fun setLoadControl(control: LoadControl?) {
        loadControl = control
    }

    override fun setDataSource(path: String?, headers: Map<String, String>?) {
        if (path != null) {
            val use = cacheConfig?.useBuiltInCache == true
            mediaSource = mediaSourceHelper.getMediaSource(path, headers, use, cacheConfig)
        }
    }

    override fun setDataSource(fd: AssetFileDescriptor?) {
        // no support
    }

    override fun start() {
        internalPlayer?.playWhenReady = true
    }

    override fun pause() {
        internalPlayer?.playWhenReady = false
    }

    override fun stop() {
        internalPlayer?.stop()
    }

    override fun prepareAsync() {
        val player = internalPlayer ?: return
        val source = mediaSource ?: return
        speedPlaybackParameters?.let { player.setPlaybackParameters(it) }
        isPreparing = true
        player.setMediaSource(source)
        player.prepare()
    }

    override fun reset() {
        internalPlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.setVideoSurface(null)
        }
        isPreparing = false
    }

    override fun isPlaying(): Boolean {
        val player = internalPlayer ?: return false
        return when (player.playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> player.playWhenReady
            else -> false
        }
    }

    override fun seekTo(time: Long) {
        internalPlayer?.seekTo(time)
    }

    override fun release() {
        internalPlayer?.let {
            it.removeListener(this)
            it.release()
        }
        internalPlayer = null
        isPreparing = false
        speedPlaybackParameters = null
    }

    override fun getCurrentPosition(): Long = internalPlayer?.currentPosition ?: 0L

    override fun getDuration(): Long = internalPlayer?.duration ?: 0L

    override fun getBufferedPercentage(): Int = internalPlayer?.bufferedPercentage ?: 0

    override fun setSurface(surface: Surface?) {
        internalPlayer?.setVideoSurface(surface)
    }

    override fun setDisplay(holder: SurfaceHolder?) {
        setSurface(holder?.surface)
    }

    override fun setVolume(v1: Float, v2: Float) {
        internalPlayer?.setVolume((v1 + v2) / 2f)
    }

    override fun setLooping(isLooping: Boolean) {
        internalPlayer?.repeatMode = if (isLooping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    override fun setOptions() {
        internalPlayer?.playWhenReady = true
    }

    override fun setSpeed(speed: Float) {
        val params = PlaybackParameters(speed)
        speedPlaybackParameters = params
        internalPlayer?.setPlaybackParameters(params)
    }

    override fun getSpeed(): Float = speedPlaybackParameters?.speed ?: 1f

    override fun getTcpSpeed(): Long = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (mPlayerEventListener == null) return
        if (isPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener?.onPrepared()
                mPlayerEventListener?.onInfo(MEDIA_INFO_RENDERING_START, 0)
                isPreparing = false
            }
            return
        }
        when (playbackState) {
            Player.STATE_BUFFERING -> mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage())
            Player.STATE_READY -> mPlayerEventListener?.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage())
            Player.STATE_ENDED -> mPlayerEventListener?.onCompletion()
            else -> { }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        mPlayerEventListener?.onError()
    }

    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
        mPlayerEventListener?.onVideoSizeChanged(videoSize.width, videoSize.height)
        @Suppress("DEPRECATION")
        val rotation = videoSize.unappliedRotationDegrees
        if (rotation > 0) {
            mPlayerEventListener?.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, rotation)
        }
    }
}
