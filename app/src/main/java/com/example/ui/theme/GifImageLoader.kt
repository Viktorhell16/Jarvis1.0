package com.example.ui.theme

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

object GifImageLoader {
    @Volatile
    private var INSTANCE: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: ImageLoader.Builder(context.applicationContext)
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
