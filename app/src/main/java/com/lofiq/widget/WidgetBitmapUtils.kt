package com.lofiq.widget

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import com.lofiq.R
import kotlin.math.max
import kotlin.math.min

object WidgetBitmapUtils {

    /**
     * Transforms a source bitmap into a full-bleed center-cropped rectangular bitmap.
     * Contains NO baked-in transparent corners or inner strokes, allowing the container
     * or ImageView with clipToOutline="true" to clip to the exact hardware-rendered corners.
     */
    fun createCenterCropBitmap(
        src: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val scale = max(targetWidth.toFloat() / src.width, targetHeight.toFloat() / src.height)
        val scaledWidth = scale * src.width
        val scaledHeight = scale * src.height
        val left = (targetWidth - scaledWidth) / 2f
        val top = (targetHeight - scaledHeight) / 2f

        val srcRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(src, null, srcRect, paint)
        return output
    }

    /**
     * Transforms a source bitmap into a center-cropped circular bitmap
     * with anti-aliasing and software ARGB_8888 config for RemoteViews.
     */
    fun createCircleBitmap(src: Bitmap, diameterPx: Int): Bitmap {
        val output = Bitmap.createBitmap(diameterPx, diameterPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = diameterPx / 2f

        // Draw circle mask
        canvas.drawCircle(radius, radius, radius, paint)

        // Center-crop source into circle
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val scale = max(diameterPx.toFloat() / src.width, diameterPx.toFloat() / src.height)
        val scaledWidth = scale * src.width
        val scaledHeight = scale * src.height
        val left = (diameterPx - scaledWidth) / 2f
        val top = (diameterPx - scaledHeight) / 2f

        val srcRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(src, null, srcRect, paint)

        // Subtle circular specular border
        paint.xfermode = null
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#33FFFFFF")
        paint.strokeWidth = 1.5f
        canvas.drawCircle(radius, radius, radius - 0.75f, paint)

        return output
    }

    /**
     * Creates a high-fidelity placeholder artwork bitmap when no album art is loaded.
     */
    fun createDefaultArtworkBitmap(
        context: Context,
        width: Int,
        height: Int,
        isCircle: Boolean = false
    ): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#172554"),
                Color.parseColor("#090D1A"),
                Shader.TileMode.CLAMP
            )
        }

        if (isCircle) {
            val radius = min(width, height) / 2f
            canvas.drawCircle(radius, radius, radius, bgPaint)
        } else {
            canvas.drawRect(rect, bgPaint)
        }

        // Draw centered music note vector icon
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_widget_music_note)
        if (drawable != null) {
            val iconSize = (min(width, height) * 0.45f).toInt()
            val iconLeft = (width - iconSize) / 2
            val iconTop = (height - iconSize) / 2
            drawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            drawable.setTint(Color.parseColor("#90FFFFFF"))
            drawable.draw(canvas)
        }

        if (isCircle) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#33FFFFFF")
                strokeWidth = 1.5f
            }
            val radius = min(width, height) / 2f
            canvas.drawCircle(radius, radius, radius - 0.75f, borderPaint)
        }

        return output
    }
}
