package cc.hhhl.client.api

import cc.hhhl.client.model.DriveFile
import cc.hhhl.client.model.DriveFileDetails
import cc.hhhl.client.model.DriveFolder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class DriveFileUpload(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val folderId: String? = null,
    val comment: String? = null,
    val isSensitive: Boolean = false,
    val force: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DriveFileUpload) return false

        return bytes.contentEquals(other.bytes) &&
            fileName == other.fileName &&
            contentType == other.contentType &&
            folderId == other.folderId &&
            comment == other.comment &&
            isSensitive == other.isSensitive &&
            force == other.force
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + isSensitive.hashCode()
        result = 31 * result + force.hashCode()
        return result
    }
}

interface DriveFileApi {
    suspend fun loadFiles(
        token: String,
        folderId: String?,
        limit: Int,
        untilId: String? = null,
        sort: DriveFileSort = DriveFileSort.CreatedDescending,
        searchQuery: String = "",
        showAll: Boolean = false,
    ): DriveFileListResult

    suspend fun loadStream(
        token: String,
        limit: Int,
        untilId: String? = null,
        type: String? = null,
    ): DriveFileListResult =
        DriveFileListResult.ServerError(501, "Drive 最近文件接口未实现")

    suspend fun loadFolders(
        token: String,
        folderId: String?,
        limit: Int,
        untilId: String? = null,
        searchQuery: String = "",
    ): DriveFolderListResult

    suspend fun uploadFile(
        token: String,
        upload: DriveFileUpload,
    ): DriveFileUploadResult

    suspend fun updateFile(
        token: String,
        fileId: String,
        name: String? = null,
        folderId: String? = null,
        comment: String? = null,
        isSensitive: Boolean? = null,
    ): DriveFileMutationResult

    suspend fun moveFile(
        token: String,
        fileId: String,
        folderId: String?,
    ): DriveFileMutationResult = updateFile(
        token = token,
        fileId = fileId,
        folderId = folderId,
    )

    suspend fun deleteFile(
        token: String,
        fileId: String,
    ): DriveFileMutationResult

    suspend fun createFolder(
        token: String,
        name: String,
        parentId: String? = null,
    ): DriveFolderMutationResult

    suspend fun updateFolder(
        token: String,
        folderId: String,
        name: String? = null,
        parentId: String? = null,
    ): DriveFolderMutationResult

    suspend fun deleteFolder(
        token: String,
        folderId: String,
    ): DriveFolderMutationResult

    suspend fun loadFileDetails(
        token: String,
        fileId: String,
    ): DriveFileDetailsResult
}

enum class DriveFileSort(
    val apiValue: String,
    val label: String,
) {
    CreatedDescending("-createdAt", "最新"),
    CreatedAscending("+createdAt", "最早"),
    NameAscending("+name", "名称 A-Z"),
    NameDescending("-name", "名称 Z-A"),
    SizeDescending("-size", "大小 ↓"),
    SizeAscending("+size", "大小 ↑"),
}

sealed interface DriveFileListResult {
    data class Success(val files: List<DriveFile>) : DriveFileListResult

    data object Unauthorized : DriveFileListResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFileListResult

    data class NetworkError(val message: String) : DriveFileListResult
}

sealed interface DriveFolderListResult {
    data class Success(val folders: List<DriveFolder>) : DriveFolderListResult

    data object Unauthorized : DriveFolderListResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFolderListResult

    data class NetworkError(val message: String) : DriveFolderListResult
}

sealed interface DriveFileUploadResult {
    data class Success(val file: DriveFile) : DriveFileUploadResult

    data object Unauthorized : DriveFileUploadResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFileUploadResult

    data class NetworkError(val message: String) : DriveFileUploadResult
}

sealed interface DriveFileMutationResult {
    data class Success(val file: DriveFile) : DriveFileMutationResult

    data object Deleted : DriveFileMutationResult

    data object Unauthorized : DriveFileMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFileMutationResult

    data class NetworkError(val message: String) : DriveFileMutationResult
}

sealed interface DriveFolderMutationResult {
    data class Success(val folder: DriveFolder) : DriveFolderMutationResult

    data object Deleted : DriveFolderMutationResult

    data object Unauthorized : DriveFolderMutationResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFolderMutationResult

    data class NetworkError(val message: String) : DriveFolderMutationResult
}

sealed interface DriveFileDetailsResult {
    data class Success(val details: DriveFileDetails) : DriveFileDetailsResult

    data object Unauthorized : DriveFileDetailsResult

    data class ServerError(
        val statusCode: Int,
        val message: String,
    ) : DriveFileDetailsResult

    data class NetworkError(val message: String) : DriveFileDetailsResult
}

class SharkeyDriveFileApi(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = defaultDriveFileClient(),
) : DriveFileApi {
    override suspend fun loadFileDetails(
        token: String,
        fileId: String,
    ): DriveFileDetailsResult {
        val cleanToken = token.trim()
        val cleanFileId = fileId.trim()
        if (cleanToken.isEmpty()) return DriveFileDetailsResult.Unauthorized
        if (cleanFileId.isEmpty()) {
            return DriveFileDetailsResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件",
            )
        }

        return try {
            val fileResponse = client.post(apiUrl("drive", "files", "show")) {
                contentType(ContentType.Application.Json)
                setBody(DriveFileShowRequest(i = cleanToken, fileId = cleanFileId))
            }
            if (fileResponse.isSharkeyUnauthorized()) return DriveFileDetailsResult.Unauthorized
            if (fileResponse.status != HttpStatusCode.OK) {
                return DriveFileDetailsResult.ServerError(
                    statusCode = fileResponse.status.value,
                    message = fileResponse.apiErrorMessage() ?: "服务器返回 ${fileResponse.status.value}",
                )
            }

            val attachedNotesResponse = client.post(apiUrl("drive", "files", "attached-notes")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFileAttachedNotesRequest(
                        i = cleanToken,
                        fileId = cleanFileId,
                        limit = 20,
                    ),
                )
            }
            if (attachedNotesResponse.isSharkeyUnauthorized()) return DriveFileDetailsResult.Unauthorized
            if (attachedNotesResponse.status != HttpStatusCode.OK) {
                return DriveFileDetailsResult.ServerError(
                    statusCode = attachedNotesResponse.status.value,
                    message = attachedNotesResponse.apiErrorMessage() ?: "服务器返回 ${attachedNotesResponse.status.value}",
                )
            }

            DriveFileDetailsResult.Success(
                DriveFileDetails(
                    file = fileResponse.body<DriveFileDto>().toDomainFile(),
                    attachedNotes = attachedNotesResponse.body<List<SharkeyNoteDto>>().map { it.toDomainNote() },
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileDetailsResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFiles(
        token: String,
        folderId: String?,
        limit: Int,
        untilId: String?,
        sort: DriveFileSort,
        searchQuery: String,
        showAll: Boolean,
    ): DriveFileListResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return DriveFileListResult.Unauthorized

        return try {
            val response = client.post(apiUrl("drive", "files")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFilesRequest(
                        i = cleanToken,
                        folderId = folderId?.takeIf { it.isNotBlank() },
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        sort = sort.apiValue,
                        searchQuery = searchQuery.trim(),
                        showAll = showAll,
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFileListResult.Success(
                    response.body<List<DriveFileDto>>().map { it.toDomainFile() },
                )
                response.isSharkeyUnauthorized() -> DriveFileListResult.Unauthorized
                else -> DriveFileListResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileListResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadStream(
        token: String,
        limit: Int,
        untilId: String?,
        type: String?,
    ): DriveFileListResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return DriveFileListResult.Unauthorized
        return try {
            val response = client.post(apiUrl("drive", "stream")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveStreamRequest(
                        i = cleanToken,
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        type = type?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            when {
                response.status == HttpStatusCode.OK -> DriveFileListResult.Success(
                    response.body<List<DriveFileDto>>().map { it.toDomainFile() },
                )
                response.isSharkeyUnauthorized() -> DriveFileListResult.Unauthorized
                else -> DriveFileListResult.ServerError(
                    response.status.value,
                    response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileListResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun loadFolders(
        token: String,
        folderId: String?,
        limit: Int,
        untilId: String?,
        searchQuery: String,
    ): DriveFolderListResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return DriveFolderListResult.Unauthorized

        return try {
            val response = client.post(apiUrl("drive", "folders")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFoldersRequest(
                        i = cleanToken,
                        folderId = folderId?.takeIf { it.isNotBlank() },
                        limit = limit.coerceIn(1, 100),
                        untilId = untilId?.takeIf { it.isNotBlank() },
                        searchQuery = searchQuery.trim(),
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFolderListResult.Success(
                    response.body<List<DriveFolderDto>>().map { it.toDomainFolder() },
                )
                response.isSharkeyUnauthorized() -> DriveFolderListResult.Unauthorized
                else -> DriveFolderListResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFolderListResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun uploadFile(
        token: String,
        upload: DriveFileUpload,
    ): DriveFileUploadResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return DriveFileUploadResult.Unauthorized

        return try {
            val response = client.post(apiUrl("drive", "files", "create")) {
                setBody(upload.toMultipart(cleanToken))
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFileUploadResult.Success(
                    response.body<DriveFileDto>().toDomainFile(),
                )
                response.isSharkeyUnauthorized() -> DriveFileUploadResult.Unauthorized
                else -> DriveFileUploadResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileUploadResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateFile(
        token: String,
        fileId: String,
        name: String?,
        folderId: String?,
        comment: String?,
        isSensitive: Boolean?,
    ): DriveFileMutationResult {
        val cleanToken = token.trim()
        val cleanFileId = fileId.trim()
        if (cleanToken.isEmpty()) return DriveFileMutationResult.Unauthorized
        if (cleanFileId.isEmpty()) {
            return DriveFileMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件",
            )
        }

        return try {
            val response = client.post(apiUrl("drive", "files", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFileUpdateRequest(
                        i = cleanToken,
                        fileId = cleanFileId,
                        folderId = folderId?.trim()?.takeIf { it.isNotEmpty() },
                        name = name?.trim()?.takeIf { it.isNotEmpty() },
                        comment = comment?.trim()?.takeIf { it.isNotEmpty() },
                        isSensitive = isSensitive,
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFileMutationResult.Success(
                    response.body<DriveFileDto>().toDomainFile(),
                )
                response.isSharkeyUnauthorized() -> DriveFileMutationResult.Unauthorized
                else -> DriveFileMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun moveFile(
        token: String,
        fileId: String,
        folderId: String?,
    ): DriveFileMutationResult {
        val cleanToken = token.trim()
        val cleanFileId = fileId.trim()
        val cleanFolderId = folderId?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanToken.isEmpty()) return DriveFileMutationResult.Unauthorized
        if (cleanFileId.isEmpty()) {
            return DriveFileMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件",
            )
        }

        return try {
            val response = client.post(apiUrl("drive", "files", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("i", JsonPrimitive(cleanToken))
                        put("fileId", JsonPrimitive(cleanFileId))
                        put("folderId", cleanFolderId?.let { JsonPrimitive(it) } ?: JsonNull)
                    },
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFileMutationResult.Success(
                    response.body<DriveFileDto>().toDomainFile(),
                )
                response.isSharkeyUnauthorized() -> DriveFileMutationResult.Unauthorized
                else -> DriveFileMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun deleteFile(
        token: String,
        fileId: String,
    ): DriveFileMutationResult {
        val cleanToken = token.trim()
        val cleanFileId = fileId.trim()
        if (cleanToken.isEmpty()) return DriveFileMutationResult.Unauthorized
        if (cleanFileId.isEmpty()) {
            return DriveFileMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件",
            )
        }

        return try {
            val response = client.post(apiUrl("drive", "files", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFileDeleteRequest(
                        i = cleanToken,
                        fileId = cleanFileId,
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent -> DriveFileMutationResult.Deleted
                response.isSharkeyUnauthorized() -> DriveFileMutationResult.Unauthorized
                else -> DriveFileMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFileMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun createFolder(
        token: String,
        name: String,
        parentId: String?,
    ): DriveFolderMutationResult {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return DriveFolderMutationResult.Unauthorized

        return try {
            val response = client.post(apiUrl("drive", "folders", "create")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFolderCreateRequest(
                        i = cleanToken,
                        name = name.trim().takeIf { it.isNotEmpty() },
                        parentId = parentId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFolderMutationResult.Success(
                    response.body<DriveFolderDto>().toDomainFolder(),
                )
                response.isSharkeyUnauthorized() -> DriveFolderMutationResult.Unauthorized
                else -> DriveFolderMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFolderMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun updateFolder(
        token: String,
        folderId: String,
        name: String?,
        parentId: String?,
    ): DriveFolderMutationResult {
        val cleanToken = token.trim()
        val cleanFolderId = folderId.trim()
        if (cleanToken.isEmpty()) return DriveFolderMutationResult.Unauthorized
        if (cleanFolderId.isEmpty()) {
            return DriveFolderMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件夹",
            )
        }

        return try {
            val response = client.post(apiUrl("drive", "folders", "update")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFolderUpdateRequest(
                        i = cleanToken,
                        folderId = cleanFolderId,
                        name = name?.trim()?.takeIf { it.isNotEmpty() },
                        parentId = parentId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK -> DriveFolderMutationResult.Success(
                    response.body<DriveFolderDto>().toDomainFolder(),
                )
                response.isSharkeyUnauthorized() -> DriveFolderMutationResult.Unauthorized
                else -> DriveFolderMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFolderMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    override suspend fun deleteFolder(
        token: String,
        folderId: String,
    ): DriveFolderMutationResult {
        val cleanToken = token.trim()
        val cleanFolderId = folderId.trim()
        if (cleanToken.isEmpty()) return DriveFolderMutationResult.Unauthorized
        if (cleanFolderId.isEmpty()) {
            return DriveFolderMutationResult.ServerError(
                statusCode = HttpStatusCode.BadRequest.value,
                message = "请选择文件夹",
            )
        }

        return try {
            val response = client.post(apiUrl("drive", "folders", "delete")) {
                contentType(ContentType.Application.Json)
                setBody(
                    DriveFolderDeleteRequest(
                        i = cleanToken,
                        folderId = cleanFolderId,
                    ),
                )
            }

            when {
                response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NoContent -> DriveFolderMutationResult.Deleted
                response.isSharkeyUnauthorized() -> DriveFolderMutationResult.Unauthorized
                else -> DriveFolderMutationResult.ServerError(
                    statusCode = response.status.value,
                    message = response.apiErrorMessage() ?: "服务器返回 ${response.status.value}",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            DriveFolderMutationResult.NetworkError(error.message ?: "网络请求失败")
        }
    }

    private fun DriveFileUpload.toMultipart(token: String): MultiPartFormDataContent {
        return MultiPartFormDataContent(
            formData {
                append("i", token)
                folderId?.trim()?.takeIf { it.isNotEmpty() }?.let { append("folderId", it) }
                fileName.trim().takeIf { it.isNotEmpty() }?.let { append("name", it) }
                comment?.trim()?.takeIf { it.isNotEmpty() }?.let { append("comment", it) }
                append("isSensitive", isSensitive.toString())
                append("force", force.toString())
                append(
                    key = "file",
                    value = bytes,
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.File
                                .withParameter(ContentDisposition.Parameters.Name, "file")
                                .withParameter(ContentDisposition.Parameters.FileName, fileName)
                                .toString(),
                        )
                    },
                )
            },
        )
    }

    private fun apiUrl(vararg endpoint: String): String {
        return URLBuilder(baseUrl.trim().trimEnd('/'))
            .appendPathSegments("api", *endpoint)
            .buildString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://dc.hhhl.cc"
    }
}

@Serializable
private data class DriveFilesRequest(
    val i: String,
    val folderId: String? = null,
    val limit: Int,
    val untilId: String? = null,
    val sort: String,
    val searchQuery: String = "",
    val showAll: Boolean,
)

@Serializable
private data class DriveStreamRequest(
    val i: String,
    val limit: Int,
    val untilId: String? = null,
    val type: String? = null,
)

@Serializable
private data class DriveFileShowRequest(
    val i: String,
    val fileId: String,
)

@Serializable
private data class DriveFileAttachedNotesRequest(
    val i: String,
    val fileId: String,
    val limit: Int,
)

@Serializable
private data class DriveFoldersRequest(
    val i: String,
    val folderId: String? = null,
    val limit: Int,
    val untilId: String? = null,
    val searchQuery: String = "",
)

@Serializable
private data class DriveFileUpdateRequest(
    val i: String,
    val fileId: String,
    val folderId: String? = null,
    val name: String? = null,
    val comment: String? = null,
    val isSensitive: Boolean? = null,
)

@Serializable
private data class DriveFileDeleteRequest(
    val i: String,
    val fileId: String,
)

@Serializable
private data class DriveFolderCreateRequest(
    val i: String,
    val name: String? = null,
    val parentId: String? = null,
)

@Serializable
private data class DriveFolderUpdateRequest(
    val i: String,
    val folderId: String,
    val name: String? = null,
    val parentId: String? = null,
)

@Serializable
private data class DriveFolderDeleteRequest(
    val i: String,
    val folderId: String,
)

@Serializable
private data class DriveFileDto(
    val id: String,
    val createdAt: String = "",
    val name: String,
    val type: String = "",
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val comment: String? = null,
    val size: Long = 0,
    val isSensitive: Boolean = false,
    val folderId: String? = null,
) {
    fun toDomainFile(): DriveFile {
        return DriveFile(
            id = id,
            name = name,
            type = type,
            url = url,
            thumbnailUrl = thumbnailUrl,
            comment = comment,
            size = size,
            isSensitive = isSensitive,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
            folderId = folderId,
        )
    }
}

@Serializable
private data class DriveFolderDto(
    val id: String,
    val createdAt: String = "",
    val name: String,
    val parentId: String? = null,
    val foldersCount: Int = 0,
    val filesCount: Int = 0,
) {
    fun toDomainFolder(): DriveFolder {
        return DriveFolder(
            id = id,
            name = name,
            parentId = parentId,
            foldersCount = foldersCount,
            filesCount = filesCount,
            createdAtLabel = createdAt.toLocalCompactDateLabel(),
        )
    }
}

@Serializable
private data class DriveFileErrorEnvelope(
    val error: DriveFileErrorDto? = null,
)

@Serializable
private data class DriveFileErrorDto(
    val message: String? = null,
)

private suspend fun HttpResponse.apiErrorMessage(): String? {
    return runCatching { sharkeyApiErrorMessage() }.getOrNull()
}


private fun defaultDriveFileClient(): HttpClient {
    return HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
}
