package com.youki.dex.activities

import android.content.Intent
import android.os.Bundle
import com.youki.dex.utils.OnboardingPrefs

/**
 * Main app entry point. First launch runs onboarding; subsequent launches
 * open DevDex settings so users can configure the app. The desktop remains available
 * through the Home/launcher entry point.
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

        // The app icon must always provide a reliable way into settings.
        // LauncherActivity is a transparent desktop surface and may look blank
        // on a fresh install with no desktop shortcuts, so it is not the app-icon
        // destination. The desktop can still be opened through the Home launcher.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
