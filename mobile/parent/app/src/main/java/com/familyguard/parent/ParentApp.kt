package com.familyguard.parent

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.familyguard.parent.data.SessionStore
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class ParentApp : Application(), ImageLoaderFactory {
    lateinit var session: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionStore(this)
    }

    override fun newImageLoader(): ImageLoader {
        val http = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http_img"), 80L * 1024 * 1024))
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(http)
            .crossfade(220)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.28).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: ParentApp
            private set
    }
}
