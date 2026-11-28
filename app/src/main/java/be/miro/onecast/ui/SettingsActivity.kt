package be.miro.onecast.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.util.SeslRoundedCorner
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import be.miro.onecast.R
import be.miro.onecast.data.AppSettings
import be.miro.onecast.databinding.ActivitySettingsBinding

/** App settings: each preference category is a rounded surface group on the page. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var amoledApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must precede inflation — the accent overlay only reaches views inflated after it.
        ExpressiveTheme.apply(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonAsBack()

        amoledApplied = AmoledTheme.isActive(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        if (AmoledTheme.isActive(this) != amoledApplied) recreate()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Write into the same prefs file AppSettings reads, so changes reach playback directly.
            preferenceManager.sharedPreferencesName = AppSettings.PREFS_NAME
            setPreferencesFromResource(R.xml.settings, rootKey)

            (findPreference<MultiSelectListPreference>(AppSettings.KEY_PLAYBACK_SPEEDS))?.let { pref ->
                updateSpeedsSummary(pref, pref.values)
                pref.setOnPreferenceChangeListener { _, newValue ->
                    @Suppress("UNCHECKED_CAST")
                    updateSpeedsSummary(pref, newValue as? Set<String> ?: emptySet())
                    true
                }
            }

            // Toggling pure-black re-themes the whole app; rebuild this screen for instant feedback.
            findPreference<Preference>(AppSettings.KEY_AMOLED_BLACK)?.setOnPreferenceChangeListener { _, _ ->
                view?.post { activity?.recreate() }
                true
            }

            // The accent overlay is layered on in onCreate, so it only reaches a freshly inflated
            // screen; rebuild so this one recolours under the user straight away.
            findPreference<Preference>(AppSettings.KEY_EXPRESSIVE_COLOR)?.setOnPreferenceChangeListener { _, _ ->
                view?.post { activity?.recreate() }
                true
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            if (AmoledTheme.isActive(requireContext())) flattenForAmoled() else applyCardStyle()
        }

        /**
         * Give each [androidx.preference.PreferenceCategory] the design system's list-group look: a
         * rounded `surface` card on the page, its section label sitting in the gap above it, inset
         * by the 12dp screen gutter. The SESL framework already draws this shape, but paints its
         * corners from the page-coloured `sesl_round_and_bgcolor_*`, so the colours are set here:
         *  - the RecyclerView background paints the card body behind the rows,
         *  - the fill below the last item is the window colour so the card ends and the surface
         *    shows through underneath,
         *  - the private rounded-corner painters are recoloured to the card so the corners match the
         *    body instead of vanishing (their colour is baked in from the flattened resource),
         *  - the category subheaders are painted the window colour so they read as gaps between cards.
         */
        private fun applyCardStyle() {
            val card = ContextCompat.getColor(requireContext(), R.color.app_surface)
            val window = ContextCompat.getColor(requireContext(), R.color.app_content_background)
            seslSetRoundedCorner(true)
            listView.setBackgroundColor(card)
            listView.seslSetFillBottomEnabled(true)
            listView.seslSetFillBottomColor(window)
            // `mRoundedCorner` rounds each PreferenceCategory item group (the visible cards), so it
            // must match the card body. `mListRoundedCorner` rounds the RecyclerView's *outer*
            // bounds — but the list's top is a gap (the first section label sits above the first
            // card), so painting those outer corners the card colour draws stray card-coloured arcs
            // in the gap above the first section. Paint them the window colour so they vanish into
            // the background instead.
            recolorRoundedCorner("mRoundedCorner", card)
            recolorRoundedCorner("mListRoundedCorner", window)
            recolorRoundedCorner("mSubheaderRoundedCorner", window)
            // The last card's bottom corners are rounded by the RecyclerView's *own* painter (used
            // with the fill-bottom fill), not the PreferenceFragment's. Its colour is baked in from
            // the theme's roundedCornerColor at construction and `seslSetFillBottomColor` doesn't
            // touch it, so it could disagree with the page colour under the last card. Repaint it the
            // window colour so the last card rounds off cleanly into the background.
            recolorListViewRoundedCorner(window)
            paintSubheaders(window)
            setCardSideMargins(resources.getDimensionPixelSize(R.dimen.settings_card_horizontal_margin))
        }

        /** Pure-black mode: the cards turn black, so drop them and let the list sit flat on the page. */
        private fun flattenForAmoled() {
            seslSetRoundedCorner(false)
            listView.seslSetFillBottomEnabled(false)
            paintSubheaders(null)
            setCardSideMargins(0)
        }

        /** Inset the RecyclerView itself (not just its content) so the card floats with a gap. */
        private fun setCardSideMargins(marginPx: Int) {
            listView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginStart = marginPx
                marginEnd = marginPx
            }
        }

        /**
         * Recolour one of [PreferenceFragmentCompat]'s private `SeslRoundedCorner` painters. Their
         * colour is fixed at construction from `sesl_round_and_bgcolor` (globally flattened to the
         * window background in this app), and there's no public setter, so reflect the field and
         * repaint it. SESL versions are pinned in the build, so the field names are stable.
         */
        private fun recolorRoundedCorner(fieldName: String, color: Int) {
            runCatching {
                val field = PreferenceFragmentCompat::class.java.getDeclaredField(fieldName)
                field.isAccessible = true
                (field.get(this) as? SeslRoundedCorner)
                    ?.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, color)
            }
        }

        /**
         * Recolour the SESL [RecyclerView]'s own last-corner painter (a private `mRoundedCorner`
         * used to round the last item over the fill-bottom fill). Like the fragment's painters its
         * colour is baked in at construction with no public setter, so reflect and repaint it.
         */
        private fun recolorListViewRoundedCorner(color: Int) {
            runCatching {
                val field = RecyclerView::class.java.getDeclaredField("mRoundedCorner")
                field.isAccessible = true
                (field.get(listView) as? SeslRoundedCorner)
                    ?.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, color)
            }
        }

        /**
         * Paint every category subheader [color] (or clear it with `null`), now and as rows recycle.
         * A category header tags its view "preferencecategory"; in card mode the window colour makes
         * it a gap between cards, in pure-black mode a null background flattens it onto the page.
         */
        private fun paintSubheaders(color: Int?) {
            fun apply(view: View) {
                if (view.tag == "preferencecategory") {
                    if (color == null) view.background = null else view.setBackgroundColor(color)
                }
            }
            for (i in 0 until listView.childCount) apply(listView.getChildAt(i))
            listView.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(child: View) = apply(child)
                    override fun onChildViewDetachedFromWindow(child: View) = Unit
                },
            )
        }

        /** Summarise the enabled speeds as a sorted, comma-separated list (e.g. "0.8×, 1.0×, 1.5×"). */
        private fun updateSpeedsSummary(pref: MultiSelectListPreference, values: Set<String>) {
            pref.summary = values.mapNotNull { it.toFloatOrNull() }
                .sorted()
                .joinToString(", ") { "${it}×" }
                .ifEmpty { getString(R.string.settings_speeds_empty) }
        }
    }
}
