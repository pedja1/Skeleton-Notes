package org.skynetsoftware.skeletonnotes.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Installs the production login launcher that uses Custom Tabs.
 * Only compiled in the `full` flavor.
 */
object NextcloudLoginHelperWiring {
    init {
        NextcloudLoginHelper.launcher = launcher@{ activity, url ->
            val uri = Uri.parse(url)
            val customTabsPackage = CustomTabsClient.getPackageName(activity, null)
            if (customTabsPackage != null) {
                try {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.intent.setPackage(customTabsPackage)
                    customTabsIntent.launchUrl(activity, uri)
                    return@launcher
                } catch (_: ActivityNotFoundException) {
                }
            }
            val intent =
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast
                    .makeText(
                        activity,
                        activity.getString(org.skynetsoftware.skeletonnotes.R.string.nextcloud_no_browser),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }
}
