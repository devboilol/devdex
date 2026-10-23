package com.youki.dex.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.youki.dex.R
import com.youki.dex.utils.AppUtils

/** Step 5 — confirmation screen. OnboardingActivity's Next button becomes "Finish" here.
 *
 * Offers links to the DevDex repository and GitHub Issues for support. */
class OnboardingDoneFragment : Fragment(R.layout.fragment_onboarding_done) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.row_onboarding_github).setOnClickListener {
            AppUtils.openUrl(requireContext(), "https://github.com/devboilol/devdex")
        }
        view.findViewById<View>(R.id.row_onboarding_report_issue).setOnClickListener {
            AppUtils.openUrl(requireContext(), "https://github.com/devboilol/devdex/issues")
        }
    }
}
