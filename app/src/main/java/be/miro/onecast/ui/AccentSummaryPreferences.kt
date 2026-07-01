package be.miro.onecast.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceViewHolder
import be.miro.onecast.R

/**
 * One UI Settings colours the *value* summary of a picker row (e.g. "15 seconds", "HEVC") in the
 * accent blue, while descriptive subtitles stay grey. Plain [ListPreference]/[MultiSelectListPreference]
 * draw every summary grey, so these variants tint their own summary blue on bind. Use them in the
 * settings XML for rows whose summary is the current selection; leave descriptive toggles as-is.
 */
private fun tintSummaryAccent(context: Context, holder: PreferenceViewHolder) {
    (holder.findViewById(android.R.id.summary) as? TextView)
        ?.setTextColor(ContextCompat.getColor(context, R.color.app_primary))
}

/** [ListPreference] whose value summary is drawn in the One UI accent colour. */
class AccentListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        tintSummaryAccent(context, holder)
    }
}

/** [MultiSelectListPreference] whose value summary is drawn in the One UI accent colour. */
class AccentMultiSelectListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MultiSelectListPreference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        tintSummaryAccent(context, holder)
    }
}
