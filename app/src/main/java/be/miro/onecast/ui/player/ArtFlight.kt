package be.miro.onecast.ui.player

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Carries the artwork down into the mini-player's artwork slot while the player is dragged away,
 * so it lands exactly where it grew out of instead of sliding off the bottom with the rest.
 *
 * The artwork can't make the trip itself: it lives inside the sheet, which is translated down and
 * faded out as it goes. So for the length of the drag a stand-in takes its place, in the window's
 * content frame *beside* the sheet, where neither the sheet's translation nor its fade reach it.
 *
 * [target] is the mini-player artwork's bounds on screen, handed over by the screen that opened the
 * player. Without it (opened from the widget), or while a video shows in the artwork's place, there
 * is nowhere to fly to and the player just slides away whole, as before.
 */
internal class ArtFlight(private val art: ImageView, private val target: Rect?) {

    private val host: ViewGroup? = art.rootView.findViewById(android.R.id.content)
    private val cornerRadius = art.resources.displayMetrics.density * CORNER_DP

    private var ghost: ImageView? = null
    private var restLeft = 0f
    private var restTop = 0f
    private var restWidth = 0
    private var restHeight = 0

    /** The stand-in's outline radius, in its own (unscaled) pixels. */
    private var outlineRadius = 0f

    /** How far the sheet has to travel for the artwork to arrive: 0 when there's no flight. */
    private var distance = 0f

    /** 0 = the artwork where it rests in the player, 1 = over the mini-player's artwork. */
    var progress = 0f
        private set

    /** Place the artwork for a sheet dragged [sheetOffset] pixels down, starting the flight if needed. */
    fun follow(sheetOffset: Float) {
        if (!begin(sheetOffset)) return
        // Linear in the drag, so the artwork stays under the finger vertically all the way down.
        fly(sheetOffset / distance)
    }

    /** Place the artwork [fraction] of the way to the mini-player. */
    fun fly(fraction: Float) {
        val ghost = ghost ?: return
        val target = target ?: return
        val host = host ?: return
        val p = fraction.coerceIn(0f, 1f)
        progress = p
        val hostOrigin = IntArray(2).also { host.getLocationOnScreen(it) }
        val scaleX = lerp(1f, target.width().toFloat() / restWidth, p)
        val scaleY = lerp(1f, target.height().toFloat() / restHeight, p)
        ghost.scaleX = scaleX
        ghost.scaleY = scaleY
        ghost.translationX = lerp(restLeft, target.left.toFloat(), p) - hostOrigin[0]
        ghost.translationY = lerp(restTop, target.top.toFloat(), p) - hostOrigin[1]
        ghost.elevation = lerp(art.elevation, 0f, p)
        // The outline is drawn scaled with the view; divide the scale back out so the corners keep
        // the same on-screen radius the whole way, matching the mini-player's artwork on landing.
        outlineRadius = cornerRadius / scaleX
        ghost.invalidateOutline()
    }

    /** The sheet is back at rest: hand the artwork its own place again. */
    fun land() {
        val ghost = ghost ?: return
        host?.removeView(ghost)
        this.ghost = null
        progress = 0f
        art.visibility = View.VISIBLE
    }

    private fun begin(sheetOffset: Float): Boolean {
        if (ghost != null) return true
        val target = target ?: return false
        val host = host ?: return false
        if (art.visibility != View.VISIBLE || art.width == 0 || art.height == 0) return false
        val location = IntArray(2).also { art.getLocationOnScreen(it) }
        restLeft = location[0].toFloat()
        // Measure from where the artwork rests, not from wherever the sheet has got to.
        restTop = location[1] - sheetOffset
        restWidth = art.width
        restHeight = art.height
        outlineRadius = cornerRadius
        distance = target.top - restTop
        // The mini-player sits below the artwork; anything else means the bounds are stale.
        if (distance <= 0f) return false

        val stand = ImageView(art.context).apply {
            layoutParams = FrameLayout.LayoutParams(restWidth, restHeight)
            scaleType = art.scaleType
            // A copy of each drawable, so the real artwork keeps its own when it's shown again.
            setImageDrawable(art.drawable?.let { it.constantState?.newDrawable(resources) ?: it })
            background = art.background?.let { it.constantState?.newDrawable(resources) ?: it }
            pivotX = 0f
            pivotY = 0f
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, outlineRadius)
                }
            }
            clipToOutline = true
        }
        host.addView(stand)
        ghost = stand
        art.visibility = View.INVISIBLE
        return true
    }

    private fun lerp(from: Float, to: Float, fraction: Float) = from + (to - from) * fraction

    private companion object {
        /** Both artworks' corner radius on screen (the placeholder's and the mini-player's). */
        const val CORNER_DP = 12f
    }
}
