package org.mozilla.fenix.onboarding

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.fenix.distributions.DistributionIdManager
import org.mozilla.fenix.onboarding.view.OnboardingPageUiData
import org.mozilla.fenix.settings.registerOnSharedPreferenceChangeListener
import org.mozilla.fenix.utils.Settings
import kotlin.coroutines.CoroutineContext

class RemoveMarketingPage(
    private val prefKey: String,
    private val pagesToDisplay: MutableList<OnboardingPageUiData>,
    private val distributionIdManager: DistributionIdManager,
    private val settings: Settings,
) {
    @VisibleForTesting
    private var isPartnership: Boolean? = null
    var currentPageIndex: Int = 0

    suspend fun launch(
        lifecycleOwner: LifecycleOwner,
        ioContext: CoroutineContext = Dispatchers.IO,
    ) {
        isPartnership = withContext(ioContext) {
            distributionIdManager.isPartnershipDistribution()
        }

        settings.preferences.registerOnSharedPreferenceChangeListener(
            lifecycleOwner,
            ::observeMarketingPreference,
        )
    }

    fun observeMarketingPreference(preferences: SharedPreferences, key: String?) {
        if (key != prefKey) {
            return
        }

        val marketingIndex =
            pagesToDisplay.indexOfFirst { it.type == OnboardingPageUiData.Type.MARKETING_DATA }

        if (!settings.shouldShowMarketingOnboarding &&
            currentPageIndex < marketingIndex &&
            isPartnership == false
        ) {
            pagesToDisplay.removeAt(marketingIndex)
        }
    }
}
