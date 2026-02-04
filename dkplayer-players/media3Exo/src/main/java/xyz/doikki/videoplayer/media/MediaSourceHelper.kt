package xyz.doikki.videoplayer.media

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * Media3 媒体源辅助类，根据 URI 创建对应的 MediaSource（DASH / HLS / RTSP / RTMP / 渐进式）。
 */
object MediaSourceHelper {

    @Volatile
    private var instance: MediaSourceHelperImpl? = null

    @JvmStatic
    fun getInstance(context: Context): MediaSourceHelperImpl {
        return instance ?: synchronized(this) {
            instance ?: MediaSourceHelperImpl(context.applicationContext).also { instance = it }
        }
    }
}

class MediaSourceHelperImpl(private val appContext: Context) {

    private val userAgent: String = Util.getUserAgent(appContext, appContext.applicationInfo.name)
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private var cache: Cache? = null

    fun getMediaSource(uri: String): MediaSource =
        getMediaSource(uri, null, false)

    fun getMediaSource(uri: String, headers: Map<String, String>?): MediaSource =
        getMediaSource(uri, headers, false)

    fun getMediaSource(uri: String, isCache: Boolean): MediaSource =
        getMediaSource(uri, null, isCache)

    fun getMediaSource(uri: String, headers: Map<String, String>?, isCache: Boolean): MediaSource {
        val contentUri = Uri.parse(uri)
        when (contentUri.scheme) {
            "rtmp" -> {
                return ProgressiveMediaSource.Factory(RtmpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(contentUri))
            }
            "rtsp" -> {
                return RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri))
            }
        }
        val contentType = inferContentType(uri)
        val factory: DataSource.Factory = if (isCache) {
            cacheDataSourceFactory()
        } else {
            dataSourceFactory()
        }
        httpDataSourceFactory?.let { setHeaders(headers, it) }
        return when (contentType) {
            C.CONTENT_TYPE_DASH -> {
                DashMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri))
            }
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri))
            }
            else -> {
                ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri))
            }
        }
    }

    private fun inferContentType(fileName: String): @C.ContentType Int {
        val lower = fileName.lowercase()
        return when {
            lower.contains(".mpd") -> C.CONTENT_TYPE_DASH
            lower.contains(".m3u8") -> C.CONTENT_TYPE_HLS
            else -> C.CONTENT_TYPE_OTHER
        }
    }

    private fun cacheDataSourceFactory(): DataSource.Factory {
        val c = cache ?: newCache().also { cache = it }
        return CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(dataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun newCache(): Cache {
        val dir = File(appContext.getExternalCacheDir(), "media3-video-cache")
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024),
            StandaloneDatabaseProvider(appContext)
        )
    }

    private fun dataSourceFactory(): DataSource.Factory {
        return DefaultDataSource.Factory(appContext, httpDataSourceFactory())
    }

    private fun httpDataSourceFactory(): DefaultHttpDataSource.Factory {
        if (httpDataSourceFactory == null) {
            httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
        }
        return httpDataSourceFactory!!
    }

    private fun setHeaders(headers: Map<String, String>?, factory: DefaultHttpDataSource.Factory) {
        if (headers.isNullOrEmpty()) return
        val mutableHeaders = ConcurrentHashMap(headers)
        mutableHeaders["User-Agent"]?.takeIf { TextUtils.isEmpty(it).not() }?.let { ua ->
            try {
                val field = factory.javaClass.getDeclaredField("userAgent")
                field.isAccessible = true
                field.set(factory, ua)
            } catch (_: Exception) { }
            mutableHeaders.remove("User-Agent")
        }
        factory.setDefaultRequestProperties(mutableHeaders)
    }

    fun setCache(cache: Cache) {
        this.cache = cache
    }
}
