package com.lofiq.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * 2×2 Compact Square Music Player Widget.
 * Minimal, clean, artwork-focused home screen music controller.
 */
class CompactSquareWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MusicWidgetManager.updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            MusicWidgetManager.updateAllWidgets(context)
        }
    }
}

