package com.youki.dex.onboarding

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.youki.dex.R

/** Welcome step: choose the app language before continuing setup. */
class OnboardingWelcomeFragment : Fragment(R.layout.fragment_onboarding_welcome) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val values = resources.getStringArray(R.array.app_language_values)
        val spinner = view.findViewById<Spinner>(R.id.onboarding_language_spinner)
        val adapter = ArrayAdapter.createFromResource(
            ctx, R.array.app_language_entries, android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        spinner.setSelection(values.indexOf(prefs.getString("app_language", "system")).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                val selectedLanguage = values.getOrNull(position) ?: return
                val savedLanguage = prefs.getString("app_language", "system") ?: "system"

                // Spinner emits an initial selection as soon as it is attached.
                // Ignore it when it matches the saved value; apply only a real change.
                if (selectedLanguage == savedLanguage) return

                prefs.edit { putString("app_language", selectedLanguage) }
                AppCompatDelegate.setApplicationLocales(
                    if (selectedLanguage == "system") LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(selectedLanguage)
                )
                // AppCompat recreates the activity for the selected locale, so labels
                // throughout onboarding update immediately instead of waiting for Done.
            }
        }
    }
}
