package be.miro.onecast.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.util.SeslRoundedCorner
import androidx.core.content.ContextCompat
import be.miro.onecast.R
import be.miro.onecast.appSettings
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.RoundFrameLayout
import dev.oneuiproject.oneui.widget.RoundLinearLayout

/**
 * Optional pure-black ("AMOLED") variant of the dark theme. One UI's dark surfaces are a near-black
 * grey (#171717) that the SESL widgets read straight from colour *resources*, so a theme/attribute
 * override can't switch them at runtime. Instead we recolour the surfaces in place: everything the
 * app paints with [R.color.oui_background_color] becomes true black, including the rounded corners
 * the [ToolbarLayout] draws around its content card. Only applies while the system is in dark mode.
 */
object AmoledTheme {

    /** True when the user opted into pure black *and* the system is currently in dark mode. */
    fun isActive(context: Context): Boolean {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return night && context.appSettings.amoledBlack
    }

    /**
     * Recolours [toolbar] and the window chrome to true black. No-op unless [isActive]. Call after
     * `setContentView`, passing the screen's [ToolbarLayout] (or null for screens without one).
     */
    fun apply(activity: Activity, toolbar: ToolbarLayout?) {
        if (!isActive(activity)) return
        val black = Color.BLACK
        activity.window.statusBarColor = black
        activity.window.navigationBarColor = black
        activity.window.decorView.setBackgroundColor(black)
        val surface = ContextCompat.getColor(activity, R.color.app_content_background)
        toolbar?.let {
            recolor(it, surface, black)
            it.appBarLayout.setBackgroundColor(black)
        }
    }

    private fun recolor(view: View, from: Int, to: Int) {
        when (view) {
            is RoundFrameLayout -> {
                view.setBackgroundColor(to)
                view.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, to)
            }
            is RoundLinearLayout -> {
                view.setBackgroundColor(to)
                view.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, to)
            }
            else -> (view.background as? ColorDrawable)?.let {
                if (it.color == from) view.setBackgroundColor(to)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) recolor(view.getChildAt(i), from, to)
        }
    }
}
