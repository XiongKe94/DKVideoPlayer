
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import xyz.doikki.videoplayer.media3.CacheConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Media3 媒体源辅助类，根据 URI 创建对应的 MediaSource（DASH / HLS / RTSP / RTMP / 渐进式）。
 */
object Media3ExoSourceHelper {

    @Volatile
    private var instance: Media3ExoSourceHelperImpl? = null

    @JvmStatic
    fun getInstance(context: Context): Media3ExoSourceHelperImpl {
        return instance ?: synchronized(this) {
            instance ?: Media3ExoSourceHelperImpl(context.applicationContext).also { instance = it }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
class Media3ExoSourceHelperImpl(private val appContext: Context) {
    private val userAgent: String = Util.getUserAgent(appContext, appContext.applicationInfo.name)
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private val caches = ConcurrentHashMap<String, Cache>()

    fun getMediaSource(uri: String, headers: Map<String, String>?, isCache: Boolean, cacheConfig: CacheConfig? = null): MediaSource {
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
            cacheDataSourceFactory(cacheConfig)
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

    private fun cacheDataSourceFactory(cacheConfig: CacheConfig?): DataSource.Factory {
        val cache = getOrCreateCache(cacheConfig)
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(dataSourceFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheConfig?.cacheKeyResolver?.let { resolver ->
            factory.setCacheKeyFactory { dataSpec -> resolver.resolveKey(dataSpec.uri.toString()) }
        }
        return factory
    }

    private fun getOrCreateCache(cacheConfig: CacheConfig?): Cache {
        val dir = cacheConfig?.cacheDir ?: File(appContext.externalCacheDir, "media3Exo-video-cache")
        val maxBytes = cacheConfig?.cacheMaxBytes ?: (512L * 1024 * 1024)
        val key = dir.absolutePath + "_" + maxBytes
        return caches.getOrPut(key) {
            SimpleCache(dir,
                LeastRecentlyUsedCacheEvictor(maxBytes),
                StandaloneDatabaseProvider(appContext)
            )
        }
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

}
