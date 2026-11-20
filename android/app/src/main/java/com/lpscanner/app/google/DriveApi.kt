package com.lpscanner.app.google

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class GoogleUser(
    val displayName: String? = null,
    val emailAddress: String,
)

@Serializable
data class DrivePerson(val displayName: String? = null, val emailAddress: String? = null)

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    /** RFC 3339, e.g. 2026-11-20T10:15:30.000Z */
    val modifiedTime: String? = null,
    val ownedByMe: Boolean = false,
    val owners: List<DrivePerson> = emptyList(),
    /** Not from Drive: a sheet the user opened by pasting its link, remembered on the device. */
    @Transient val openedByLink: Boolean = false,
)

/** One entry of a file's sharing list. https://developers.google.com/drive/api/reference/rest/v3/permissions */
@Serializable
data class DrivePermission(
    /** user, group, domain or anyone */
    val type: String,
    /** owner, organizer, fileOrganizer, writer, commenter or reader */
    val role: String,
    val emailAddress: String? = null,
    val displayName: String? = null,
    val domain: String? = null,
    /** For type=anyone: true = findable on the web, false = anyone with the link. */
    val allowFileDiscovery: Boolean? = null,
)

/** Google Drive v3 REST calls. Read-only: the app never changes files or sharing through Drive. */
class DriveApi(
    private val client: GoogleApiClient,
    private val baseUrl: HttpUrl = "https://www.googleapis.com/drive/v3/".toHttpUrl(),
) {
    @Serializable
    private data class About(val user: GoogleUser)

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class FileSharing(val permissions: List<DrivePermission>? = null)

    /** Who is signed in. Also proves the token works against Drive. */
    suspend fun currentUser(): GoogleUser {
        val url = baseUrl.newBuilder()
            .addPathSegment("about")
            .addQueryParameter("fields", "user(displayName,emailAddress)")
            .build()
        return client.get(url, About.serializer()).user
    }

    /**
     * Spreadsheets this app can see, most recently modified first. With the drive.file scope
     * that's the ones it created (or the user opened with it), not the whole Drive.
     */
    suspend fun listSpreadsheets(): List<DriveFile> {
        val url = baseUrl.newBuilder()
            .addPathSegment("files")
            .addQueryParameter("q", "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "100")
            .addQueryParameter("fields", "files(id,name,modifiedTime,ownedByMe,owners(displayName,emailAddress))")
            .build()
        return client.get(url, FileList.serializer()).files
    }

    /**
     * Who the file is shared with, or null when Google doesn't show it to this user
     * (e.g. they only have view access). A 404 means the file isn't one this app created.
     */
    suspend fun permissions(fileId: String): List<DrivePermission>? {
        val url = baseUrl.newBuilder()
            .addPathSegment("files")
            .addPathSegment(fileId)
            .addQueryParameter("fields", "permissions(type,role,emailAddress,displayName,domain,allowFileDiscovery)")
            .build()
        return client.get(url, FileSharing.serializer()).permissions
    }
}
