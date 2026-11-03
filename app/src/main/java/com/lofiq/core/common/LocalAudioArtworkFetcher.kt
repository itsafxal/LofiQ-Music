package com.lofiq.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioArtworkFetcher(
    private val context: Context,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data.toString().startsWith("content://")) {
                try {
                    context.contentResolver.loadThumbnail(data, Size(512, 512), null)
                } catch (e: Exception) {
                    extractWithRetriever(context, data)
                }
            } else {
                extractWithRetriever(context, data)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } else {
            null
        }
    }

    private fun extractWithRetriever(context: Context, uri: Uri): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            val rawBytes = mmr.embeddedPicture
            if (rawBytes != null) {
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            } else null
        } catch (e: Exception) {
            null
        } finally {
            try {
                mmr.release()
            } catch (ignored: Exception) {}
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val str = data.toString()
            if (str.startsWith("http://") || str.startsWith("https://")) {
                return null
            }
            if (str.contains("media/external/audio") || 
                str.contains("content://media") || 
                str.endsWith(".mp3", ignoreCase = true) || 
                str.endsWith(".m4a", ignoreCase = true) || 
                str.endsWith(".flac", ignoreCase = true) || 
                str.endsWith(".wav", ignoreCase = true) || 
                str.endsWith(".ogg", ignoreCase = true)) {
                return LocalAudioArtworkFetcher(context, data)
            }
            return null
        }
    }
}
