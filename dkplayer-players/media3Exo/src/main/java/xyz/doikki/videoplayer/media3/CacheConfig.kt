package xyz.doikki.videoplayer.media3

import java.io.File

class CacheConfig private constructor(
    val useBuiltInCache: Boolean,
    val cacheDir: File?,
    val cacheMaxBytes: Long,
    val cacheKeyResolver: CacheKeyResolver?
) {
    class Builder {
        private var useBuiltInCache = false
        private var cacheDir: File? = null
        private var cacheMaxBytes = DEFAULT_CACHE_MAX_BYTES
        private var cacheKeyResolver: CacheKeyResolver? = null

        fun setUseBuiltInCache(use: Boolean) = apply { useBuiltInCache = use }
        fun setCacheDir(dir: File?) = apply { this.cacheDir = dir }
        fun setCacheMaxBytes(bytes: Long) = apply { cacheMaxBytes = bytes }
        fun setCacheKeyResolver(resolver: CacheKeyResolver?) = apply { cacheKeyResolver = resolver }
        fun build() = CacheConfig(useBuiltInCache, cacheDir, cacheMaxBytes, cacheKeyResolver)
    }

    companion object {
        const val DEFAULT_CACHE_MAX_BYTES = 512L * 1024 * 1024
        @JvmStatic
        fun builder() = Builder()
    }
}
