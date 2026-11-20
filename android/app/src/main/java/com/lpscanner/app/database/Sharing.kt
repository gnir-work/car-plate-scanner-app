package com.lpscanner.app.database

import com.lpscanner.app.google.DrivePermission

/** Who can open a sheet, as far as the signed-in user can see. */
sealed interface Sharing {
    data object Private : Sharing

    data class People(val count: Int) : Sharing

    /** Everyone in a Google Workspace domain, e.g. a company. Broad but not public. */
    data class Domain(val domain: String) : Sharing

    /** Anyone with the link (or findable on the web). The thing we must warn about. */
    data class Public(val discoverable: Boolean) : Sharing

    /** Google doesn't show the sharing list to this user (view-only access). */
    data object Unknown : Sharing

    /** Opened from a link: the app's Drive permission doesn't cover reading its sharing. */
    data object Hidden : Sharing

    companion object {
        fun from(permissions: List<DrivePermission>?): Sharing {
            if (permissions == null) return Unknown
            permissions.firstOrNull { it.type == "anyone" }?.let { return Public(discoverable = it.allowFileDiscovery == true) }
            permissions.firstOrNull { it.type == "domain" }?.let { return Domain(it.domain ?: "your organization") }
            val others = permissions.count { it.role != "owner" }
            return if (others == 0) Private else People(others)
        }
    }
}
