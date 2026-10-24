package com.youki.dex.activities

import android.content.Intent
import android.os.Bundle
import com.youki.dex.utils.OnboardingPrefs

/**
 * Main app entry point. First launch runs onboarding; subsequent launches
 * open the DevDex desktop. Settings remain available from the desktop dock.
 */
class SplashActivity : BaseFontScaleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First launch — route to onboarding.
        if (!OnboardingPrefs.isOnboardingComplete(this)) {
            startActivity(Intent(this, com.youki.dex.onboarding.OnboardingActivity::class.java))
            finish()
            overridePendingTransition(0, 0)
            return
        }

        // Desktop is the default entry point after setup.
        startActivity(
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
