package org.mozilla.fenix.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mozilla.components.support.base.feature.LifecycleAwareFeature
import org.mozilla.fenix.distributions.DistributionIdManager
import org.mozilla.fenix.onboarding.view.OnboardingPageUiData
import org.mozilla.fenix.settings.OnSharedPreferenceChangeListener
import org.mozilla.fenix.utils.Settings
import kotlin.coroutines.CoroutineContext

class RemoveMarketingPage(
    private val prefKey: String,
    private val pagesToDisplay: MutableList<OnboardingPageUiData>,
    private val distributionIdManager: DistributionIdManager,
    private val settings: Settings,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val lifecycleOwner: LifecycleOwner,
) : LifecycleAwareFeature {
    var currentPageIndex: Int = 0
    private var job: Job? = null
    override fun start() {
        job = lifecycleOwner.lifecycleScope.launch(ioContext) {
            val isPartnership = distributionIdManager.isPartnershipDistribution()

            settings.preferences.flowScopedBooleanPreference(
                lifecycleOwner,
                prefKey,
                settings.shouldShowMarketingOnboarding,
            )
                .distinctUntilChanged()
                .collect { shouldShowMarketingOnboarding ->
                    if (!shouldShowMarketingOnboarding && !isPartnership) {
                        pagesToDisplay.removeIfPageNotReached(currentPageIndex)
                    }
                }
        }
    }

    override fun stop() {
        job?.cancel()
    }
}

internal fun MutableList<OnboardingPageUiData>.removeIfPageNotReached(index: Int) {
    val marketingIndex = indexOfFirst { it.type == OnboardingPageUiData.Type.MARKETING_DATA }

    if (index < marketingIndex) {
        removeAt(marketingIndex)
    }
}

internal fun SharedPreferences.flowScopedBooleanPreference(
    owner: LifecycleOwner,
    key: String,
    defValue: Boolean,
) = channelFlow {
    val listener = OnSharedPreferenceChangeListener(
        this@flowScopedBooleanPreference,
    ) { pref, updatedKey ->
        if (key == updatedKey) {
            val result = pref.getBoolean(key, defValue)
            runBlocking {
                send(result)
            }
            this@channelFlow.close()
        }
    }

    withContext(Dispatchers.Main) {
        owner.lifecycle.addObserver(listener)
    }

    val initValue = getBoolean(key, defValue)
    send(initValue)

    awaitClose {
        // On the off-chance that we close unexpectedly, let's clean up.
        unregisterOnSharedPreferenceChangeListener(listener)
    }
}.buffer(Channel.CONFLATED)
