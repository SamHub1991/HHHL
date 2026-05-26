package cc.hhhl.client.repository

import cc.hhhl.client.api.ComposeApi
import cc.hhhl.client.api.ComposeCreateResult
import cc.hhhl.client.api.ComposeDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class ComposeRepositoryTest {
    @Test
    fun sendUsesTokenProviderAndApi() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        val result = repository.send(ComposeDraft(text = "hello"))

        assertEquals(ComposeRepositoryResult.Success("note-created"), result)
        assertEquals(listOf(ApiCall("token-123", "hello", null, null, null, emptyList())), calls)
    }

    @Test
    fun sendPreservesReplyIdInDraft() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        repository.send(ComposeDraft(text = "reply", replyId = "note-parent"))

        assertEquals(listOf(ApiCall("token-123", "reply", "note-parent", null, null, emptyList())), calls)
    }

    @Test
    fun sendPreservesRenoteIdAndFileIdsInDraft() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        repository.send(
            ComposeDraft(
                text = "quote",
                renoteId = "note-quote",
                fileIds = listOf("file-1", "file-2"),
            ),
        )

        assertEquals(
            listOf(ApiCall("token-123", "quote", null, "note-quote", null, listOf("file-1", "file-2"))),
            calls,
        )
    }

    @Test
    fun sendAllowsFileOnlyDraft() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        val result = repository.send(ComposeDraft(text = " ", fileIds = listOf("file-1")))

        assertEquals(ComposeRepositoryResult.Success("note-created"), result)
        assertEquals(listOf(ApiCall("token-123", " ", null, null, null, listOf("file-1"))), calls)
    }

    @Test
    fun sendPreservesChannelIdInDraft() = runTest {
        val calls = mutableListOf<ApiCall>()
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                calls = calls,
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        repository.send(ComposeDraft(text = "channel note", channelId = "channel-1"))

        assertEquals(
            listOf(ApiCall("token-123", "channel note", null, null, "channel-1", emptyList())),
            calls,
        )
    }

    @Test
    fun sendRejectsBlankTextWithoutCallingApi() = runTest {
        var calls = 0
        val repository = ComposeRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                onCall = { calls += 1 },
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        val result = repository.send(ComposeDraft(text = " "))

        assertIs<ComposeRepositoryResult.ValidationError>(result)
        assertEquals(0, calls)
    }

    @Test
    fun sendReturnsUnauthorizedWhenTokenMissing() = runTest {
        var calls = 0
        val repository = ComposeRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
                result = ComposeCreateResult.Success("note-created"),
            ),
        )

        assertIs<ComposeRepositoryResult.Unauthorized>(repository.send(ComposeDraft(text = "hello")))
        assertEquals(0, calls)
    }

    private fun fakeApi(
        calls: MutableList<ApiCall> = mutableListOf(),
        result: ComposeCreateResult,
        onCall: () -> Unit = {},
    ): ComposeApi {
        return object : ComposeApi {
            override suspend fun createNote(
                token: String,
                draft: ComposeDraft,
            ): ComposeCreateResult {
                onCall()
                calls.add(ApiCall(token, draft.text, draft.replyId, draft.renoteId, draft.channelId, draft.fileIds))
                return result
            }
        }
    }

    private data class ApiCall(
        val token: String,
        val text: String,
        val replyId: String?,
        val renoteId: String?,
        val channelId: String?,
        val fileIds: List<String>,
    )
}
