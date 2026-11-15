package com.bane23.iptvplayer.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

object DeviceType {
    /** Da li aplikacija radi na Android TV uređaju (odlučuje koji UI sloj se učitava). */
    fun isTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }
}
