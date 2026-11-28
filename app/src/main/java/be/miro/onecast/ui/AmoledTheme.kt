package be.miro.onecast.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import be.miro.onecast.R
import be.miro.onecast.appSettings

/**
 * Optional all-black variant of the dark theme. The design system's dark mode already puts every
 * screen on a pure black page; this option turns the grouped surfaces on it black as well — list
 * groups and the Settings cards — for a flat, all-black look. Floating things (the mini-player,
 * the Up Next sheet edge) keep enough of a lift to stay visible. Only applies in dark mode.
 *
 * Code that paints a group surface asks [surface] for its colour rather than reading
 * [R.color.app_surface], so it follows this option.
 */
object AmoledTheme {

    /** True when the user opted into pure black *and* the system is currently in dark mode. */
    fun isActive(context: Context): Boolean {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return night && context.appSettings.amoledBlack
    }

    /** The colour of list groups and cards: the design-system `surface`, or black in this mode. */
    @ColorInt
    fun surface(context: Context): Int =
        if (isActive(context)) Color.BLACK else ContextCompat.getColor(context, R.color.app_surface)
}
