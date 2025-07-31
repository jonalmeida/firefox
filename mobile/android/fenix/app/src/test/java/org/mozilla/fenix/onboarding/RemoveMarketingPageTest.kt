/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.onboarding

import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.test.rule.MainCoroutineRule
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.BrowserFragmentTest
import org.mozilla.fenix.distributions.DistributionIdManager
import org.mozilla.fenix.onboarding.view.OnboardingPageUiData
import org.mozilla.fenix.utils.Settings

@RunWith(AndroidJUnit4::class)
class RemoveMarketingPageTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    lateinit var pages: MutableList<OnboardingPageUiData>
    lateinit var settings: Settings
    lateinit var mockedLifecycleOwner: MockedLifecycleOwner
    lateinit var distributionIdManager: DistributionIdManager
    lateinit var prefKey: String

    @Before
    fun setup() {
        pages = mutableListOf<OnboardingPageUiData>().apply {
            add(
                OnboardingPageUiData(
                    type = OnboardingPageUiData.Type.SYNC_SIGN_IN,
                    imageRes = 0,
                    title = "sync title",
                    description = "sync body",
                    primaryButtonLabel = "sync primary button text",
                    secondaryButtonLabel = "sync secondary button text",
                    privacyCaption = null,
                ),
            )
            add(
                OnboardingPageUiData(
                    type = OnboardingPageUiData.Type.MARKETING_DATA,
                    imageRes = 0,
                    title = "marketing title",
                    description = "notification body",
                    primaryButtonLabel = "notification primary button text",
                    secondaryButtonLabel = "notification secondary button text",
                    privacyCaption = null,
                ),
            )
        }
        settings = Settings(testContext)
        mockedLifecycleOwner = MockedLifecycleOwner(Lifecycle.State.CREATED)
        distributionIdManager = mockk(relaxed = true)
        prefKey = testContext.getString(R.string.pref_key_should_show_marketing_onboarding)
    }

    @Test
    fun `happy path`() = runTest {
        val removePage = RemoveMarketingPage(
            prefKey = prefKey,
            pagesToDisplay = pages,
            distributionIdManager = distributionIdManager,
            settings = settings,
            ioContext = testScheduler,
            lifecycleOwner = mockedLifecycleOwner,
        )
        settings.shouldShowMarketingOnboarding = false

        removePage.start()

        testScheduler.advanceUntilIdle()

        assertTrue(pages.size == 1)
    }

    @Test
    fun `we should show marketing`() = runTest {
        val removePage = RemoveMarketingPage(
            prefKey = prefKey,
            pagesToDisplay = pages,
            distributionIdManager = distributionIdManager,
            settings = settings,
            ioContext = testScheduler,
            lifecycleOwner = mockedLifecycleOwner,
        )
        settings.shouldShowMarketingOnboarding = true

        removePage.start()

        testScheduler.advanceUntilIdle()

        assertTrue(pages.size == 2)
    }

    // TODO: replace with shared version
    class MockedLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this).apply {
            currentState = initialState
        }
    }
}
