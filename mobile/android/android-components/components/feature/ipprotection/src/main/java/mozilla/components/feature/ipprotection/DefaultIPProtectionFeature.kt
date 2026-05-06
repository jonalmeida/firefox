/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.ipprotection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.ipprotection.IPProtectionDelegate
import mozilla.components.concept.engine.ipprotection.IPProtectionHandler
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.support.base.log.logger.Logger

private val logger = Logger("DefaultIPProtectionFeature")
private const val TOKEN_SCOPE = "https://identity.mozilla.com/apps/vpn"

/**
 * AC feature that brings IP protection proxy functionality to Android.
 *
 * @param engine [Engine] used to register the IP protection delegate and obtain the handler.
 * @param lazyAccountManager [Lazy] wrapper around [FxaAccountManager] that is used to supply
 * FxA tokens to the proxy Guardian.
 * @param storage [IPProtectionEligibilityStorage] exposes the availability of the feature.
 * @param store [IPProtectionStore] holds the feature state.
 */
@OptIn(ExperimentalAndroidComponentsApi::class)
class DefaultIPProtectionFeature(
    private val engine: Engine,
    private val lazyAccountManager: Lazy<FxaAccountManager>,
    private val storage: IPProtectionEligibilityStorage,
    private val store: IPProtectionStore,
) : IPProtectionFeature {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var handler: IPProtectionHandler? = null

    private val accountObserver = object : AccountObserver {
        override fun onReady(authenticatedAccount: OAuthAccount?) {
            scope.launch {
                println("IPPC: notifyAccountStatus")
                handler?.notifyAccountStatus(true)
            }
        }

        override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
            store.dispatch(IPProtectionAction.AccountStateChanged(isSignedIn = true))
            scope.launch {
                println("IPPC: setAuthProvider")
                setTokenProvider(account)
                println("IPPC: enroll")
                handler?.enroll()
            }
        }

        override fun onLoggedOut() {
            store.dispatch(IPProtectionAction.AccountStateChanged(isSignedIn = false))
            scope.launch {
                handler?.setAuthProvider(null)
                handler?.notifyAccountStatus(false)
            }
        }

        override fun onAuthenticationProblems() {
            store.dispatch(IPProtectionAction.AccountStateChanged(isSignedIn = false))
            scope.launch {
                handler?.setAuthProvider(null)
                handler?.notifyAccountStatus(false)
            }
        }
    }

    /**
     * Starts listening for feature availability changes.
     */
    fun start() {
        scope.launch {
            storage.eligibilityStatus
                .distinctUntilChanged()
                .collect { eligibilityStatus ->
                    when (eligibilityStatus) {
                        EligibilityStatus.Eligible -> {
                            setUpProxy()
                        }

                        EligibilityStatus.Ineligible,
                        EligibilityStatus.UnsupportedRegion,
                            -> {
                            tearDownProxy()
                        }

                        EligibilityStatus.Unknown -> {
                            // no-op, initializing
                        }
                    }
                    store.dispatch(IPProtectionAction.EligibilityChanged(eligibilityStatus))
                }
        }
        storage.init()
    }

    private fun setUpProxy() {
        handler = engine.registerIPProtectionDelegate(
            object : IPProtectionDelegate {
                override fun onStateChanged(info: IPProtectionHandler.StateInfo) {
                    logger.debug("onStateChanged: proxyState = $info")
                    store.dispatch(IPProtectionAction.EngineStateChanged(info))
                }
            },
        )
        handler?.init()

        lazyAccountManager.value.register(accountObserver)
    }

    private fun tearDownProxy() {
        lazyAccountManager.value.unregister(accountObserver)
        engine.unregisterIPProtectionDelegate()
        handler?.setAuthProvider(null)
        handler = null
    }

    override fun activate() {
        handler?.activate()
    }

    override fun deactivate() {
        handler?.deactivate()
    }

    private fun setTokenProvider(account: OAuthAccount) {
        println("IPPC: feature setAuthProvider")
        handler?.setAuthProvider(
            provider = object : IPProtectionHandler.AuthProvider {
                override fun getToken(onComplete: (String?) -> Unit) {
                    scope.launch {
                        val tokenInfo = withContext(Dispatchers.IO) {
                            account.getAccessToken(TOKEN_SCOPE)
                        }
                        onComplete(tokenInfo?.token)
                    }
                }
            },
        )
    }
}
