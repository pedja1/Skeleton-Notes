package org.skynetsoftware.skeletonnotes.settings

import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo

sealed class NextcloudLoginState {
    object NotConnected: NextcloudLoginState()
    data class Connected(val nextcloudConnectionInfo: NextcloudConnectionInfo): NextcloudLoginState()
    object InitiatingLogin: NextcloudLoginState()
    object WaitingForLogin: NextcloudLoginState()
    object LoginError: NextcloudLoginState()
}
