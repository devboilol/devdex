package com.youki.dex.fragments

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.youki.dex.R


class DefaultAppsPreferences : PreferenceFragmentCompat() {
    override fun onCreatePreferences(arg0: Bundle?, arg1: String?) {
        setPreferencesFromResource(R.xml.preferences_default_apps, arg1)
        findPreference<Preference>("open_builtin_file_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), com.youki.dex.activities.FileManagerActivity::class.java))
            true
        }
    }
}
