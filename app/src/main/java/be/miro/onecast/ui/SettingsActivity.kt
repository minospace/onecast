package be.miro.onecast.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import be.miro.onecast.R
import be.miro.onecast.data.AppSettings
import be.miro.onecast.databinding.ActivitySettingsBinding

/** App settings, grouped into One UI preference sections. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var amoledApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarLayout.setNavigationButtonAsBack()

        amoledApplied = AmoledTheme.isActive(this)
        AmoledTheme.apply(this, binding.toolbarLayout)

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
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            // In pure-black mode the One UI preference "cards" (a grey #171717 fill + rounded
            // corners the SESL framework paints from colour resources) would float as grey panels
            // on the black background. Drop them so the list sits flat on the content surface, the
            // same way the grey panel was removed from the main/podcast/search screens.
            if (!AmoledTheme.isActive(requireContext())) return
            seslSetRoundedCorner(false)
            listView.seslSetFillBottomEnabled(false)
            // Category headers (the `listSeparatorTextViewStyle` TextView) keep their own grey
            // subheader background; clear it as rows are bound/recycled so the section labels sit
            // flat on black too.
            for (i in 0 until listView.childCount) clearSubheaderBackground(listView.getChildAt(i))
            listView.addOnChildAttachStateChangeListener(
                object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(child: View) = clearSubheaderBackground(child)
                    override fun onChildViewDetachedFromWindow(child: View) = Unit
                },
            )
        }

        /** A preference category header tags itself "preferencecategory"; strip its grey background. */
        private fun clearSubheaderBackground(view: View) {
            if (view.tag == "preferencecategory") view.background = null
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
