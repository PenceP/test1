package com.test1.tv.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class CustomGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Increase memory cache size for smoother scrolling
        val memoryCacheSizeBytes = 1024 * 1024 * 50 // 50 MB
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes.toLong()))

        // Increase disk cache size
        val diskCacheSizeBytes = 1024 * 1024 * 250 // 250 MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes.toLong()))

        // Use RGB_565 for better performance (lower quality but smoother)
        builder.setDefaultRequestOptions(
            RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        )
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}
