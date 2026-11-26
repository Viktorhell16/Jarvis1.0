package com.example.ui.theme

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

object GifImageLoader {
    @Volatile
    private var INSTANCE: ImageLoader? = null

    private const val DISK_CACHE_MAX_BYTES = 40L * 1024L * 1024L

    fun get(context: Context): ImageLoader {
        val appContext = context.applicationContext
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: ImageLoader.Builder(appContext)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appContext.cacheDir, "jarvis_image_cache"))
                        .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                        .build()
                }
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .crossfade(true)
                .build()
                .also { INSTANCE = it }
        }
    }
}
