package com.lpscanner.app.database

import com.lpscanner.app.google.DrivePermission
import org.junit.Assert.assertEquals
import org.junit.Test

class SharingTest {
    private val owner = DrivePermission(type = "user", role = "owner", emailAddress = "me@example.com")

    @Test fun `owner only is private`() = assertEquals(Sharing.Private, Sharing.from(listOf(owner)))

    @Test fun `counts people other than the owner`() = assertEquals(
        Sharing.People(2),
        Sharing.from(
            listOf(
                owner,
                DrivePermission(type = "user", role = "writer", emailAddress = "a@example.com"),
                DrivePermission(type = "group", role = "reader", emailAddress = "team@example.com"),
            ),
        ),
    )

    @Test fun `anyone with the link is public, even when also shared with people`() = assertEquals(
        Sharing.Public(discoverable = false),
        Sharing.from(
            listOf(
                owner,
                DrivePermission(type = "user", role = "writer"),
                DrivePermission(type = "anyone", role = "reader", allowFileDiscovery = false),
            ),
        ),
    )

    @Test fun `discoverable on the web is flagged`() = assertEquals(
        Sharing.Public(discoverable = true),
        Sharing.from(listOf(owner, DrivePermission(type = "anyone", role = "reader", allowFileDiscovery = true))),
    )

    @Test fun `domain sharing`() = assertEquals(
        Sharing.Domain("example.com"),
        Sharing.from(listOf(owner, DrivePermission(type = "domain", role = "reader", domain = "example.com"))),
    )

    @Test fun `hidden sharing list is unknown`() = assertEquals(Sharing.Unknown, Sharing.from(null))
}
