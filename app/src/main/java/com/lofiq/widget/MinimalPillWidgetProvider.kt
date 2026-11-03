package com.lofiq.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * 4×1 Minimal Pill Music Player Widget.
 * Modern, one-line capsule controller with circular artwork, like, and shuffle controls.
 */
class MinimalPillWidgetProvider : AppWidgetProvider() {

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

