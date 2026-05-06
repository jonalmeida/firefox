/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.ipprotection

import androidx.annotation.OptIn
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.ipprotection.IPProtectionHandler
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.IPProtectionController

@OptIn(ExperimentalGeckoViewApi::class)
@kotlin.OptIn(ExperimentalAndroidComponentsApi::class)
internal class GeckoIPProtectionHandler(
    private val runtime: GeckoRuntime,
) : IPProtectionHandler {

    override fun activate() {
        runtime.ipProtectionController.activate()
    }

    override fun deactivate() {
        runtime.ipProtectionController.deactivate()
    }

    override fun enroll() {
        runtime.ipProtectionController.enroll()
    }

    override fun init() {
        println("IPPC: init")
        runtime.ipProtectionController.init()
    }

    override fun setAuthProvider(
        provider: IPProtectionHandler.AuthProvider?,
    ) {
        println("IPPC: setAuthProvider")
        runtime.ipProtectionController.setAuthProvider(
            object : IPProtectionController.AuthProvider {
                override fun getToken(): GeckoResult<String?> {
                    println("IPPC: AuthProvider.getToken called")
                    val result = GeckoResult<String?>()
                    provider?.getToken { token ->
                        result.complete(token)
                    }
                    return result
                }
            },
        )
    }

    override fun notifyAccountStatus(signedIn: Boolean) {
        runtime.ipProtectionController.notifySignInStateChanged(signedIn)
    }
}
