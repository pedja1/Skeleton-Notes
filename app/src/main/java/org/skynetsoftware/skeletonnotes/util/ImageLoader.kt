package org.skynetsoftware.skeletonnotes.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skynetsoftware.skeletonnotes.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal, dependency-free image loader for local files.
 *
 * Decoding uses [BitmapFactory]'s two-pass technique: a bounds-only pass computes an
 * `inSampleSize` so the bitmap is downsampled to roughly the target view size (decoding a
 * full-resolution photo straight into a small view is the usual cause of `OutOfMemoryError`).
 * Decoded bitmaps are kept in a memory [LruCache]; no disk cache is needed because the files are
 * already local. Decoding runs on [Dispatchers.IO] and the result is applied on the caller's
 * (main) dispatcher.
 */
object ImageLoader {
    // Cap the cache at ~1/8 of the app's available heap, measured in KiB.
    private val cache =
        object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt(),
        ) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount / 1024
        }

    // Intrinsic width/height ratio per file. Aspect is size-independent so it is keyed by path.
    private val aspectCache = ConcurrentHashMap<String, Float>()

    /**
     * Returns the already-computed aspect ratio for [path], or `null` if it has not been read yet.
     * Lets callers set a correct ratio synchronously (no flash) for images seen earlier.
     */
    fun cachedAspectRatio(path: String): Float? = aspectCache[path]

    /**
     * Returns the intrinsic aspect ratio (`width / height`) of the image at [path], reading only
     * the file header (no pixels). Results are cached. Falls back to `1f` when the file is missing
     * or cannot be decoded. This does file I/O and must be called off the main thread
     * (e.g. on [Dispatchers.IO]).
     */
    fun aspectRatio(path: String): Float {
        aspectCache[path]?.let { return it }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val ratio =
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() / options.outHeight.toFloat()
            } else {
                1f
            }
        aspectCache[path] = ratio
        return ratio
    }

    /**
     * Loads the image at [path] into [target], downsampled to [reqWidth] x [reqHeight]. Any load
     * previously started for [target] is cancelled first so recycled views never show a stale
     * image. If the file is missing or cannot be decoded, [target]'s image is cleared.
     */
    fun load(
        target: ImageView,
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        scope: CoroutineScope,
    ) {
        (target.getTag(R.id.image_loader_job_tag) as? Job)?.cancel()

        val key = "$path@${reqWidth}x$reqHeight"
        cache.get(key)?.let {
            target.setImageBitmap(it)
            return
        }

        target.setImageDrawable(null)
        val job =
            scope.launch {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        if (!File(path).exists()) {
                            null
                        } else {
                            decodeSampled(path, reqWidth, reqHeight)?.also { cache.put(key, it) }
                        }
                    }
                if (bitmap != null) {
                    target.setImageBitmap(bitmap)
                }
            }
        target.setTag(R.id.image_loader_job_tag, job)
    }

    private fun decodeSampled(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        while (height / (inSampleSize * 2) >= reqHeight && width / (inSampleSize * 2) >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
