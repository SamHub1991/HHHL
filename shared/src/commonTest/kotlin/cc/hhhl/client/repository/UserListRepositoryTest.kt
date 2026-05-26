package cc.hhhl.client.repository

import cc.hhhl.client.api.UserListApi
import cc.hhhl.client.api.UserListActionResult
import cc.hhhl.client.api.UserListLoadResult
import cc.hhhl.client.api.UserListMutationResult
import cc.hhhl.client.api.UserListTimelineLoadResult
import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class UserListRepositoryTest {
    @Test
    fun refreshListsUsesTokenAndMapsLists() = runTest {
        val lists = listOf(sampleList("list-1"))
        val calls = mutableListOf<String>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                listCalls = calls,
                listResult = UserListLoadResult.Success(lists),
            ),
        )

        val result = repository.refreshLists()

        assertIs<UserListsRepositoryResult.Success>(result)
        assertEquals(listOf("token-123"), calls)
        assertEquals(lists, result.lists)
    }

    @Test
    fun refreshTimelineUsesTokenAndListId() = runTest {
        val calls = mutableListOf<TimelineCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                timelineCalls = calls,
                timelineResult = UserListTimelineLoadResult.Success(listOf(FakeData.timeline[0])),
            ),
        )

        val result = repository.refreshTimeline("list-1")

        assertIs<UserListTimelineRepositoryResult.Success>(result)
        assertEquals(listOf(TimelineCall("token-123", "list-1", null)), calls)
        assertEquals(listOf(FakeData.timeline[0]), result.notes)
    }

    @Test
    fun loadMoreTimelineUsesLastNoteIdAndDeduplicates() = runTest {
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val calls = mutableListOf<TimelineCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                timelineCalls = calls,
                timelineResult = UserListTimelineLoadResult.Success(listOf(second, first)),
            ),
        )

        val result = repository.loadMoreTimeline(
            listId = "list-1",
            currentNotes = listOf(first),
        )

        assertIs<UserListTimelineRepositoryResult.Success>(result)
        assertEquals(listOf(TimelineCall("token-123", "list-1", first.id)), calls)
        assertEquals(listOf(first, second), result.notes)
    }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutCallingApi() = runTest {
        var calls = 0
        val repository = UserListRepository(
            tokenProvider = { null },
            api = fakeApi(
                onCall = { calls += 1 },
            ),
        )

        assertIs<UserListsRepositoryResult.Unauthorized>(repository.refreshLists())
        assertIs<UserListTimelineRepositoryResult.Unauthorized>(repository.refreshTimeline("list-1"))
        assertEquals(0, calls)
    }

    @Test
    fun createListUsesTokenAndCleanDraft() = runTest {
        val created = sampleList("list-created")
        val calls = mutableListOf<MutationCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                mutationResult = UserListMutationResult.Success(created),
            ),
        )

        val result = repository.createList(UserListDraft(name = "  朋友  ", isPublic = true))

        assertIs<UserListMutationRepositoryResult.Success>(result)
        assertEquals(created, result.list)
        assertEquals(listOf(MutationCall("create", "token-123", null, UserListDraft("朋友", true))), calls)
    }

    @Test
    fun updateListUsesTokenIdAndCleanDraft() = runTest {
        val updated = sampleList("list-1").copy(name = "同事")
        val calls = mutableListOf<MutationCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                mutationCalls = calls,
                mutationResult = UserListMutationResult.Success(updated),
            ),
        )

        val result = repository.updateList(" list-1 ", UserListDraft(" 同事 ", isPublic = false))

        assertIs<UserListMutationRepositoryResult.Success>(result)
        assertEquals(updated, result.list)
        assertEquals(listOf(MutationCall("update", "token-123", "list-1", UserListDraft("同事", false))), calls)
    }

    @Test
    fun deleteListUsesTokenAndId() = runTest {
        val calls = mutableListOf<String>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                deleteCalls = calls,
                actionResult = UserListActionResult.Success,
            ),
        )

        assertEquals(UserListActionRepositoryResult.Success, repository.deleteList(" list-1 "))
        assertEquals(listOf("token-123:list-1"), calls)
    }

    @Test
    fun addUserToListUsesTokenListIdAndUserId() = runTest {
        val calls = mutableListOf<MemberCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                memberCalls = calls,
                actionResult = UserListActionResult.Success,
            ),
        )

        assertEquals(UserListActionRepositoryResult.Success, repository.addUserToList(" list-1 ", " user-3 "))
        assertEquals(listOf(MemberCall("push", "token-123", "list-1", "user-3")), calls)
    }

    @Test
    fun removeUserFromListUsesTokenListIdAndUserId() = runTest {
        val calls = mutableListOf<MemberCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(
                memberCalls = calls,
                actionResult = UserListActionResult.Success,
            ),
        )

        assertEquals(UserListActionRepositoryResult.Success, repository.removeUserFromList(" list-1 ", " user-2 "))
        assertEquals(listOf(MemberCall("pull", "token-123", "list-1", "user-2")), calls)
    }

    @Test
    fun memberMutationRejectsBlankUserIdWithoutCallingApi() = runTest {
        val calls = mutableListOf<MemberCall>()
        val repository = UserListRepository(
            tokenProvider = { "token-123" },
            api = fakeApi(memberCalls = calls),
        )

        assertEquals(UserListActionRepositoryResult.Error("请输入用户 ID"), repository.addUserToList("list-1", " "))
        assertEquals(UserListActionRepositoryResult.Error("请输入用户 ID"), repository.removeUserFromList("list-1", " "))
        assertEquals(emptyList(), calls)
    }

    private fun fakeApi(
        listCalls: MutableList<String> = mutableListOf(),
        timelineCalls: MutableList<TimelineCall> = mutableListOf(),
        mutationCalls: MutableList<MutationCall> = mutableListOf(),
        deleteCalls: MutableList<String> = mutableListOf(),
        memberCalls: MutableList<MemberCall> = mutableListOf(),
        listResult: UserListLoadResult = UserListLoadResult.Success(emptyList()),
        timelineResult: UserListTimelineLoadResult = UserListTimelineLoadResult.Success(emptyList()),
        mutationResult: UserListMutationResult = UserListMutationResult.Success(sampleList("list-mutated")),
        actionResult: UserListActionResult = UserListActionResult.Success,
        onCall: () -> Unit = {},
    ): UserListApi {
        return object : UserListApi {
            override suspend fun loadLists(token: String): UserListLoadResult {
                onCall()
                listCalls.add(token)
                return listResult
            }

            override suspend fun loadListTimeline(
                token: String,
                listId: String,
                limit: Int,
                untilId: String?,
                withRenotes: Boolean,
                withFiles: Boolean,
            ): UserListTimelineLoadResult {
                onCall()
                timelineCalls.add(TimelineCall(token, listId, untilId))
                return timelineResult
            }

            override suspend fun createList(
                token: String,
                draft: UserListDraft,
            ): UserListMutationResult {
                mutationCalls.add(MutationCall("create", token, null, draft))
                return mutationResult
            }

            override suspend fun updateList(
                token: String,
                listId: String,
                draft: UserListDraft,
            ): UserListMutationResult {
                mutationCalls.add(MutationCall("update", token, listId, draft))
                return mutationResult
            }

            override suspend fun deleteList(
                token: String,
                listId: String,
            ): UserListActionResult {
                deleteCalls.add("$token:$listId")
                return actionResult
            }

            override suspend fun pushUser(
                token: String,
                listId: String,
                userId: String,
            ): UserListActionResult {
                memberCalls.add(MemberCall("push", token, listId, userId))
                return actionResult
            }

            override suspend fun pullUser(
                token: String,
                listId: String,
                userId: String,
            ): UserListActionResult {
                memberCalls.add(MemberCall("pull", token, listId, userId))
                return actionResult
            }
        }
    }

    private fun sampleList(id: String): UserList {
        return UserList(
            id = id,
            name = "朋友",
            createdBy = "user-me",
            userIds = listOf("user-1", "user-2"),
            isPublic = true,
            isLiked = false,
            likedCount = 3,
            createdAtLabel = "2026-05-25 06:00",
        )
    }

    private data class TimelineCall(
        val token: String,
        val listId: String,
        val untilId: String?,
    )

    private data class MutationCall(
        val action: String,
        val token: String,
        val listId: String?,
        val draft: UserListDraft,
    )

    private data class MemberCall(
        val action: String,
        val token: String,
        val listId: String,
        val userId: String,
    )
}
