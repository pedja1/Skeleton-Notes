package org.skynetsoftware.skeletonnotes.settings

import android.app.Activity

/**
 * Helper for launching Nextcloud login URLs. The actual implementation
 * (using Custom Tabs or an external browser) is set by the `full` flavor
 * during DI initialization. In the `lite` flavor the launcher remains null
 * and login is never attempted.
 */
object NextcloudLoginHelper {
    var launcher: ((Activity, String) -> Unit)? = null
}
