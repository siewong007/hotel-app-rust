package com.hotelapp.staff.core.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class PlatformHaptics(private val view: View) {
    fun confirm() {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(flag)
    }

    fun reject() {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(flag)
    }

    fun tick() {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(flag)
    }

    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

@Composable
fun rememberPlatformHaptics(): PlatformHaptics {
    val view = LocalView.current
    return remember(view) { PlatformHaptics(view) }
}
