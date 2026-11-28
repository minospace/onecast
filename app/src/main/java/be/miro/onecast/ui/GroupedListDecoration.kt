package be.miro.onecast.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import be.miro.onecast.R

/**
 * The design system's rounded list group for a [RecyclerView]: consecutive rows that share a group
 * sit together on one `surface` card with `radius-lg` (26dp) corners, separated by inset hairlines,
 * with `space-3` (12dp) between groups. Rows keep their own 12dp side margins (`Onecast.Row`).
 *
 * The decoration owns each grouped row's background rather than the adapter, because a row's place
 * in its group (first, middle, last, alone) changes whenever a neighbour is inserted or removed —
 * often without the row itself being rebound. Working it out at draw time keeps the corners right
 * through every diff and animation.
 *
 * [groupOf] maps an adapter position to a group key: rows with equal keys next to each other form
 * one group, and `null` leaves a row (a header, say) out of any group. [dividerStartInset] is where
 * the hairline starts, measured from the group's left edge — past the artwork on rows that have
 * some, so the line runs under the text only.
 */
class GroupedListDecoration(
    context: Context,
    private val dividerStartInset: Int,
    private val groupOf: (position: Int) -> Any?,
) : RecyclerView.ItemDecoration() {

    private val radius = context.resources.getDimension(R.dimen.radius_lg)
    private val dividerEndInset = context.resources.getDimensionPixelSize(R.dimen.row_padding_horizontal)
    private val groupGap = context.resources.getDimensionPixelSize(R.dimen.gutter)
    private val surface = AmoledTheme.surface(context)
    private val ripple = ColorStateList.valueOf(context.themeColor(android.R.attr.colorControlHighlight))
    private val dividerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.app_divider)
        strokeWidth = context.resources.displayMetrics.density
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position <= 0) return
        val group = groupOf(position) ?: return
        // A gap above the first row of every group but the very first item.
        if (groupOf(position - 1) != group) outRect.top = groupGap
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            // Mid-removal: keep whatever the row last had.
            if (position == RecyclerView.NO_POSITION) continue
            val shape = shapeAt(position, count) ?: continue
            if (child.getTag(R.id.tag_group_shape) != shape) {
                child.background = background(shape)
                child.setTag(R.id.tag_group_shape, shape)
            }
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val shape = shapeAt(position, count) ?: continue
            if (shape == Shape.LAST || shape == Shape.SINGLE) continue
            val y = child.bottom + child.translationY - dividerPaint.strokeWidth / 2
            dividerPaint.alpha = (child.alpha * 255).toInt()
            c.drawLine(
                child.left + child.translationX + dividerStartInset,
                y,
                child.right + child.translationX - dividerEndInset,
                y,
                dividerPaint,
            )
        }
    }

    private fun shapeAt(position: Int, count: Int): Shape? {
        val group = groupOf(position) ?: return null
        val joinsAbove = position > 0 && groupOf(position - 1) == group
        val joinsBelow = position < count - 1 && groupOf(position + 1) == group
        return when {
            joinsAbove && joinsBelow -> Shape.MIDDLE
            joinsAbove -> Shape.LAST
            joinsBelow -> Shape.FIRST
            else -> Shape.SINGLE
        }
    }

    private fun background(shape: Shape): RippleDrawable {
        val top = if (shape == Shape.FIRST || shape == Shape.SINGLE) radius else 0f
        val bottom = if (shape == Shape.LAST || shape == Shape.SINGLE) radius else 0f
        return rowBackground(surface, ripple, top, bottom)
    }

    private enum class Shape { FIRST, MIDDLE, LAST, SINGLE }

    companion object {
        /**
         * Make [view] a one-row group on its own — for a grouped row that isn't in a list (the
         * "Create a custom podcast" row on the Add podcast screen).
         */
        fun applySingleRow(view: View) {
            val context = view.context
            val radius = context.resources.getDimension(R.dimen.radius_lg)
            view.background = rowBackground(
                AmoledTheme.surface(context),
                ColorStateList.valueOf(context.themeColor(android.R.attr.colorControlHighlight)),
                radius,
                radius,
            )
        }

        /** A row's surface, rounded on the given corners, with a ripple clipped to the same shape. */
        private fun rowBackground(surface: Int, ripple: ColorStateList, top: Float, bottom: Float): RippleDrawable {
            val fill = GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            }
            // No separate mask: the ripple is clipped to the rounded fill.
            return RippleDrawable(ripple, fill, null)
        }

        private fun Context.themeColor(attr: Int): Int {
            val typed = TypedValue()
            theme.resolveAttribute(attr, typed, true)
            return if (typed.resourceId != 0) ContextCompat.getColor(this, typed.resourceId) else typed.data
        }
    }
}
