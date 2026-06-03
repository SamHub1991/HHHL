package cc.hhhl.client.state

import cc.hhhl.client.fake.FakeData
import cc.hhhl.client.model.Note
import cc.hhhl.client.model.UserList
import cc.hhhl.client.model.UserListDraft
import cc.hhhl.client.repository.UserListActionRepositoryResult
import cc.hhhl.client.repository.UserListMutationRepositoryResult
import cc.hhhl.client.repository.UserListRepository
import cc.hhhl.client.repository.UserListTimelineRepositoryResult
import cc.hhhl.client.repository.UserListsRepositoryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserListStateHolderTest {
    @Test
    fun refreshListsStoresListsAndLoadsFirstTimeline() = runTest {
        val list = sampleList("list-1")
        val note = FakeData.timeline[0]
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(list)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        assertTrue(holder.state.value.isLoadingLists)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingLists)
        assertFalse(holder.state.value.isLoadingTimeline)
        assertEquals(listOf(list), holder.state.value.lists)
        assertEquals(list, holder.state.value.selectedList)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun selectListLoadsSelectedListTimeline() = runTest {
        val first = sampleList("list-1")
        val second = sampleList("list-2")
        val calls = mutableListOf<String>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(first, second)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                onRefreshTimeline = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.selectList(second)
        assertTrue(holder.state.value.isLoadingTimeline)
        advanceUntilIdle()

        assertEquals(second, holder.state.value.selectedList)
        assertEquals(listOf("list-1", "list-2"), calls)
    }

    @Test
    fun loadMoreAppendsTimelineAndMarksEndReached() = runTest {
        val list = sampleList("list-1")
        val first = FakeData.timeline[0]
        val second = FakeData.timeline[1]
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(list)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(first)),
                loadMoreResult = UserListTimelineRepositoryResult.Success(
                    notes = listOf(first, second),
                    endReached = true,
                ),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.loadMore()
        assertTrue(holder.state.value.isLoadingMore)
        advanceUntilIdle()

        assertFalse(holder.state.value.isLoadingMore)
        assertTrue(holder.state.value.endReached)
        assertEquals(listOf(first, second), holder.state.value.notes)
    }

    @Test
    fun applyNoteMutationClearsReloginAfterUnauthorized() = runTest {
        val list = sampleList("list-1")
        val note = FakeData.timeline[0]
        var listsResult: UserListsRepositoryResult = UserListsRepositoryResult.Success(listOf(list))
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(emptyList()),
                listsResultProvider = { listsResult },
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(note)),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        listsResult = UserListsRepositoryResult.Unauthorized
        holder.refreshLists()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        holder.applyNoteMutation(NoteLocalMutation.React(note.id, "👍"))

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(1, holder.state.value.notes.single().reactions.single { it.reaction == "👍" }.count)
    }

    @Test
    fun unauthorizedListLoadMarksRelogin() = runTest {
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Unauthorized,
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()

        assertTrue(holder.state.value.requiresRelogin)
        assertEquals("登录已失效，请重新登录", holder.state.value.errorMessage)
    }

    @Test
    fun successfulRefreshClearsReloginAfterUnauthorizedListLoad() = runTest {
        val list = sampleList("list-1")
        var listsResult: UserListsRepositoryResult = UserListsRepositoryResult.Unauthorized
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(emptyList()),
                listsResultProvider = { listsResult },
                timelineResult = UserListTimelineRepositoryResult.Success(emptyList()),
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        assertTrue(holder.state.value.requiresRelogin)

        listsResult = UserListsRepositoryResult.Success(listOf(list))
        holder.refreshLists()
        advanceUntilIdle()

        assertFalse(holder.state.value.requiresRelogin)
        assertEquals(listOf(list), holder.state.value.lists)
        assertEquals(list, holder.state.value.selectedList)
    }

    @Test
    fun createListPrependsAndSelectsCreatedList() = runTest {
        val existing = sampleList("list-1")
        val created = sampleList("list-created").copy(name = "朋友")
        val calls = mutableListOf<UserListDraft>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(existing)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                mutationResult = UserListMutationRepositoryResult.Success(created),
                onCreateList = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.createList(UserListDraft(" 朋友 ", true))
        assertTrue(holder.state.value.isMutatingList)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingList)
        assertEquals(listOf(UserListDraft("朋友", true)), calls)
        assertEquals(listOf(created, existing), holder.state.value.lists)
        assertEquals(created, holder.state.value.selectedList)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun updateSelectedListUpdatesListAndSelection() = runTest {
        val selected = sampleList("list-1")
        val updated = selected.copy(name = "同事", isPublic = true)
        val calls = mutableListOf<Pair<String, UserListDraft>>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(selected)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                mutationResult = UserListMutationRepositoryResult.Success(updated),
                onUpdateList = { listId, draft -> calls.add(listId to draft) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.updateSelectedList(UserListDraft(" 同事 ", true))
        advanceUntilIdle()

        assertEquals(listOf("list-1" to UserListDraft("同事", true)), calls)
        assertEquals(updated, holder.state.value.selectedList)
        assertEquals(updated, holder.state.value.lists.single())
        assertEquals(listOf(FakeData.timeline[0]), holder.state.value.notes)
    }

    @Test
    fun deleteSelectedListRemovesAndSelectsNext() = runTest {
        val first = sampleList("list-1")
        val second = sampleList("list-2")
        val calls = mutableListOf<String>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(first, second)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                actionResult = UserListActionRepositoryResult.Success,
                onDeleteList = { calls.add(it) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.deleteSelectedList()
        advanceUntilIdle()

        assertEquals(listOf("list-1"), calls)
        assertEquals(listOf(second), holder.state.value.lists)
        assertEquals(second, holder.state.value.selectedList)
        assertEquals(emptyList(), holder.state.value.notes)
    }

    @Test
    fun pendingDeleteDoesNotClearNewlySelectedList() = runTest {
        val deleteResult = CompletableDeferred<UserListActionRepositoryResult>()
        val first = sampleList("list-1")
        val second = sampleList("list-2")
        val note = FakeData.timeline[0]
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(first, second)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(note)),
                actionResultProvider = { deleteResult.await() },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.deleteSelectedList()
        runCurrent()

        assertTrue(holder.state.value.isMutatingList)

        holder.selectList(second)
        advanceUntilIdle()
        assertEquals(second, holder.state.value.selectedList)
        assertEquals(listOf(note), holder.state.value.notes)

        deleteResult.complete(UserListActionRepositoryResult.Success)
        advanceUntilIdle()

        assertFalse(holder.state.value.isMutatingList)
        assertEquals(listOf(second), holder.state.value.lists)
        assertEquals(second, holder.state.value.selectedList)
        assertEquals(listOf(note), holder.state.value.notes)
    }

    @Test
    fun addUserToSelectedListAddsMemberLocally() = runTest {
        val selected = sampleList("list-1")
        val calls = mutableListOf<Pair<String, String>>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(selected)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                actionResult = UserListActionRepositoryResult.Success,
                onAddUserToList = { listId, userId -> calls.add(listId to userId) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.addUserToSelectedList(" user-3 ")
        assertTrue(holder.state.value.isMutatingMembers)
        advanceUntilIdle()

        val expected = selected.copy(userIds = listOf("user-1", "user-2", "user-3"))
        assertFalse(holder.state.value.isMutatingMembers)
        assertEquals(listOf("list-1" to "user-3"), calls)
        assertEquals(expected, holder.state.value.selectedList)
        assertEquals(expected, holder.state.value.lists.single())
    }

    @Test
    fun removeUserFromSelectedListRemovesMemberLocally() = runTest {
        val selected = sampleList("list-1")
        val calls = mutableListOf<Pair<String, String>>()
        val holder = UserListStateHolder(
            repository = fakeRepository(
                listsResult = UserListsRepositoryResult.Success(listOf(selected)),
                timelineResult = UserListTimelineRepositoryResult.Success(listOf(FakeData.timeline[0])),
                actionResult = UserListActionRepositoryResult.Success,
                onRemoveUserFromList = { listId, userId -> calls.add(listId to userId) },
            ),
            scope = TestScope(testScheduler),
        )

        holder.refreshLists()
        advanceUntilIdle()
        holder.removeUserFromSelectedList(" user-2 ")
        advanceUntilIdle()

        val expected = selected.copy(userIds = listOf("user-1"))
        assertEquals(listOf("list-1" to "user-2"), calls)
        assertEquals(expected, holder.state.value.selectedList)
        assertEquals(expected, holder.state.value.lists.single())
    }

    private fun fakeRepository(
        listsResult: UserListsRepositoryResult,
        timelineResult: UserListTimelineRepositoryResult = UserListTimelineRepositoryResult.Success(emptyList()),
        loadMoreResult: UserListTimelineRepositoryResult = timelineResult,
        mutationResult: UserListMutationRepositoryResult = UserListMutationRepositoryResult.Success(sampleList("list-mutated")),
        actionResult: UserListActionRepositoryResult = UserListActionRepositoryResult.Success,
        onRefreshTimeline: (String) -> Unit = {},
        onCreateList: (UserListDraft) -> Unit = {},
        onUpdateList: (String, UserListDraft) -> Unit = { _, _ -> },
        onDeleteList: (String) -> Unit = {},
        onAddUserToList: (String, String) -> Unit = { _, _ -> },
        onRemoveUserFromList: (String, String) -> Unit = { _, _ -> },
        listsResultProvider: (() -> UserListsRepositoryResult)? = null,
        actionResultProvider: suspend (String) -> UserListActionRepositoryResult = { actionResult },
    ): UserListRepository {
        return object : UserListRepository(
            tokenProvider = { "token-123" },
            api = object : cc.hhhl.client.api.UserListApi {
                override suspend fun loadLists(token: String): cc.hhhl.client.api.UserListLoadResult {
                    return cc.hhhl.client.api.UserListLoadResult.Success(emptyList())
                }

                override suspend fun loadListTimeline(
                    token: String,
                    listId: String,
                    limit: Int,
                    untilId: String?,
                    withRenotes: Boolean,
                    withFiles: Boolean,
                ): cc.hhhl.client.api.UserListTimelineLoadResult {
                    return cc.hhhl.client.api.UserListTimelineLoadResult.Success(emptyList())
                }

                override suspend fun createList(
                    token: String,
                    draft: UserListDraft,
                ): cc.hhhl.client.api.UserListMutationResult {
                    return cc.hhhl.client.api.UserListMutationResult.Success(sampleList("list-mutated"))
                }

                override suspend fun updateList(
                    token: String,
                    listId: String,
                    draft: UserListDraft,
                ): cc.hhhl.client.api.UserListMutationResult {
                    return cc.hhhl.client.api.UserListMutationResult.Success(sampleList("list-mutated"))
                }

                override suspend fun deleteList(
                    token: String,
                    listId: String,
                ): cc.hhhl.client.api.UserListActionResult {
                    return cc.hhhl.client.api.UserListActionResult.Success
                }

                override suspend fun pushUser(
                    token: String,
                    listId: String,
                    userId: String,
                ): cc.hhhl.client.api.UserListActionResult {
                    return cc.hhhl.client.api.UserListActionResult.Success
                }

                override suspend fun pullUser(
                    token: String,
                    listId: String,
                    userId: String,
                ): cc.hhhl.client.api.UserListActionResult {
                    return cc.hhhl.client.api.UserListActionResult.Success
                }
            },
        ) {
            override suspend fun refreshLists(): UserListsRepositoryResult {
                return listsResultProvider?.invoke() ?: listsResult
            }

            override suspend fun refreshTimeline(listId: String): UserListTimelineRepositoryResult {
                onRefreshTimeline(listId)
                return timelineResult
            }

            override suspend fun loadMoreTimeline(
                listId: String,
                currentNotes: List<Note>,
            ): UserListTimelineRepositoryResult {
                return loadMoreResult
            }

            override suspend fun createList(draft: UserListDraft): UserListMutationRepositoryResult {
                onCreateList(draft)
                return mutationResult
            }

            override suspend fun updateList(
                listId: String,
                draft: UserListDraft,
            ): UserListMutationRepositoryResult {
                onUpdateList(listId, draft)
                return mutationResult
            }

            override suspend fun deleteList(listId: String): UserListActionRepositoryResult {
                onDeleteList(listId)
                return actionResultProvider(listId)
            }

            override suspend fun addUserToList(
                listId: String,
                userId: String,
            ): UserListActionRepositoryResult {
                onAddUserToList(listId, userId)
                return actionResult
            }

            override suspend fun removeUserFromList(
                listId: String,
                userId: String,
            ): UserListActionRepositoryResult {
                onRemoveUserFromList(listId, userId)
                return actionResult
            }
        }
    }

    private fun sampleList(id: String): UserList {
        return UserList(
            id = id,
            name = "List $id",
            createdBy = "user-me",
            userIds = listOf("user-1", "user-2"),
            isPublic = false,
            isLiked = false,
            likedCount = 0,
            createdAtLabel = "2026-05-25 06:00",
        )
    }
}
