package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class ExoMediaSourceHelper {

    private static volatile ExoMediaSourceHelper sInstance;

    private final String mUserAgent;
    private final Context mAppContext;
    private HttpDataSource.Factory mHttpDataSourceFactory;
    private final Map<String, Cache> mCaches = new ConcurrentHashMap<>();

    private ExoMediaSourceHelper(Context context) {
        mAppContext = context.getApplicationContext();
        mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
    }

    public static ExoMediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ExoMediaSourceHelper.class) {
                if (sInstance == null) {
                    sInstance = new ExoMediaSourceHelper(context);
                }
            }
        }
        return sInstance;
    }

    public MediaSource getMediaSource(String uri) {
        return getMediaSource(uri, null, false, null);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache, CacheConfig cacheConfig) {
        Uri contentUri = Uri.parse(uri);
        if ("rtmp".equals(contentUri.getScheme())) {
            return new ProgressiveMediaSource.Factory(new RtmpDataSource.Factory())
                    .createMediaSource(MediaItem.fromUri(contentUri));
        } else if ("rtsp".equals(contentUri.getScheme())) {
            return new RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri));
        }
        int contentType = inferContentType(uri);
        DataSource.Factory factory;
        if (isCache) {
            factory = getCacheDataSourceFactory(cacheConfig);
        } else {
            factory = getDataSourceFactory();
        }
        if (mHttpDataSourceFactory != null) {
            setHeaders(headers);
        }
        switch (contentType) {
            case C.CONTENT_TYPE_DASH:
                return new DashMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
            default:
            case C.CONTENT_TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
        }
    }

    private int inferContentType(String fileName) {
        fileName = fileName.toLowerCase();
        if (fileName.contains(".mpd")) {
            return C.CONTENT_TYPE_DASH;
        } else if (fileName.contains(".m3u8")) {
            return C.CONTENT_TYPE_HLS;
        } else {
            return C.CONTENT_TYPE_OTHER;
        }
    }

    private DataSource.Factory getCacheDataSourceFactory(CacheConfig cacheConfig) {
        Cache cache = getOrCreateCache(cacheConfig);
        CacheDataSource.Factory factory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(getDataSourceFactory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        if (cacheConfig != null && cacheConfig.cacheKeyResolver != null) {
            final CacheKeyResolver resolver = cacheConfig.cacheKeyResolver;
            factory.setCacheKeyFactory(new CacheKeyFactory() {
                @Override
                public String buildCacheKey(DataSpec dataSpec) {
                    return resolver.resolveKey(dataSpec.uri.toString());
                }
            });
        }
        return factory;
    }

    private Cache getOrCreateCache(CacheConfig cacheConfig) {
        File dir = cacheConfig != null && cacheConfig.cacheDir != null
                ? cacheConfig.cacheDir
                : new File(mAppContext.getExternalCacheDir(), "exo-video-cache");
        long maxBytes = cacheConfig != null ? cacheConfig.cacheMaxBytes : (512L * 1024 * 1024);
        String key = dir.getAbsolutePath() + "_" + maxBytes;
        Cache cache = mCaches.get(key);
        if (cache == null) {
            cache = new SimpleCache(
                    dir,
                    new LeastRecentlyUsedCacheEvictor(maxBytes),
                    new StandaloneDatabaseProvider(mAppContext));
            mCaches.put(key, cache);
        }
        return cache;
    }

    /**
     * Returns a new DataSource factory.
     *
     * @return A new DataSource factory.
     */
    private DataSource.Factory getDataSourceFactory() {
        return new DefaultDataSource.Factory(mAppContext, getHttpDataSourceFactory());
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @return A new HttpDataSource factory.
     */
    private DataSource.Factory getHttpDataSourceFactory() {
        if (mHttpDataSourceFactory == null) {
            mHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(mUserAgent)
                    .setAllowCrossProtocolRedirects(true);
        }
        return mHttpDataSourceFactory;
    }

    private void setHeaders(Map<String, String> headers) {
        if (headers != null && headers.size() > 0) {
            //如果发现用户通过header传递了UA，则强行将HttpDataSourceFactory里面的userAgent字段替换成用户的
            if (headers.containsKey("User-Agent")) {
                String value = headers.remove("User-Agent");
                if (!TextUtils.isEmpty(value)) {
                    try {
                        Field userAgentField = mHttpDataSourceFactory.getClass().getDeclaredField("userAgent");
                        userAgentField.setAccessible(true);
                        userAgentField.set(mHttpDataSourceFactory, value);
                    } catch (Exception e) {
                        //ignore
                    }
                }
            }
            mHttpDataSourceFactory.setDefaultRequestProperties(headers);
        }
    }

}
