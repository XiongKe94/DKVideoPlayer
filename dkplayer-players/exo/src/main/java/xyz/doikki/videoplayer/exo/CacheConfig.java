package xyz.doikki.videoplayer.exo;



import androidx.annotation.Nullable;

import java.io.File;

public class CacheConfig {

    public static final long DEFAULT_CACHE_MAX_BYTES = 512L * 1024 * 1024;

    public final boolean useBuiltInCache;
    @Nullable
    public final File cacheDir;
    public final long cacheMaxBytes;
    @Nullable
    public final CacheKeyResolver cacheKeyResolver;

    private CacheConfig(Builder b) {
        this.useBuiltInCache = b.useBuiltInCache;
        this.cacheDir = b.cacheDir;
        this.cacheMaxBytes = b.cacheMaxBytes;
        this.cacheKeyResolver = b.cacheKeyResolver;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean useBuiltInCache = false;
        private File cacheDir = null;
        private long cacheMaxBytes = DEFAULT_CACHE_MAX_BYTES;
        private CacheKeyResolver cacheKeyResolver = null;

        public Builder setUseBuiltInCache(boolean use) {
            this.useBuiltInCache = use;
            return this;
        }

        public Builder setCacheDir(@Nullable File dir) {
            this.cacheDir = dir;
            return this;
        }

        public Builder setCacheMaxBytes(long bytes) {
            this.cacheMaxBytes = bytes;
            return this;
        }

        public Builder setCacheKeyResolver(@Nullable CacheKeyResolver resolver) {
            this.cacheKeyResolver = resolver;
            return this;
        }

        public CacheConfig build() {
            return new CacheConfig(this);
        }
    }
}
