package cc.hhhl.client.fake

import cc.hhhl.client.model.Note
import cc.hhhl.client.model.NoteMedia
import cc.hhhl.client.model.NotificationItem
import cc.hhhl.client.model.NotificationType
import cc.hhhl.client.model.User

object FakeData {
    val me = User(
        id = "u0",
        displayName = "HHHL",
        username = "hhhl",
        avatarInitial = "H",
        bio = "期待 AGI 时代来临。这里是一个轻量客户端的本地预览。",
        followersCount = 128,
        followingCount = 42,
        notesCount = 860,
        isFollowing = false,
    )

    private val lin = User(
        id = "u1",
        displayName = "林间",
        username = "lin",
        avatarInitial = "林",
        bio = "写代码，读论文，偶尔发长文。",
        followersCount = 92,
        followingCount = 66,
        notesCount = 312,
        isFollowing = true,
    )
    private val qing = User(
        id = "u2",
        displayName = "青石",
        username = "qing",
        avatarInitial = "青",
        bio = "本地时间线观察员。",
        followersCount = 41,
        followingCount = 28,
        notesCount = 97,
        isFollowing = false,
    )
    private val xia = User(
        id = "u3",
        displayName = "夏夜",
        username = "xia",
        avatarInitial = "夏",
        bio = "喜欢安静的界面。",
        followersCount = 203,
        followingCount = 120,
        notesCount = 540,
        isFollowing = true,
    )

    val timeline = listOf(
        Note(
            id = "n1",
            author = lin,
            text = "把移动端先做成信息流优先是对的。很多功能可以后补，但阅读、回复、发帖这三件事必须顺手。",
            createdAtLabel = "2 分钟",
            replyCount = 3,
            renoteCount = 4,
            reactionCount = 18,
        ),
        Note(
            id = "n2",
            author = qing,
            text = "今天把 Sharkey 的移动端页面又看了一遍，网页已经能用，但原生客户端还是应该更快一点：少一点装饰，多一点直接。",
            createdAtLabel = "18 分钟",
            media = listOf(
                NoteMedia("m1", "时间线截图草稿"),
                NoteMedia("m2", "发帖界面草稿"),
            ),
            replyCount = 1,
            renoteCount = 2,
            reactionCount = 9,
        ),
        Note(
            id = "n3",
            author = xia,
            text = "长文本测试：轻量客户端不等于简陋。真正关键的是把重复使用的路径压短，比如打开应用就能看到时间线，点一次进入回复，两步完成发帖，失败时不要清掉已经写好的内容。界面可以非常克制，但状态和错误处理要扎实。",
            createdAtLabel = "1 小时",
            replyCount = 8,
            renoteCount = 7,
            reactionCount = 31,
        ),
        Note(
            id = "n4",
            author = lin,
            text = "RE: 这个方向可以。先用假数据把布局和导航跑顺，再接 MiAuth 和真实时间线。",
            createdAtLabel = "2 小时",
            replyCount = 0,
            renoteCount = 1,
            reactionCount = 12,
            isRenote = true,
        ),
    )

    val notifications = listOf(
        NotificationItem("noti1", NotificationType.Reaction, lin, "对你的帖子做出了反应", "刚刚", "n1"),
        NotificationItem("noti2", NotificationType.Reply, qing, "回复了你的帖子", "12 分钟", "n2"),
        NotificationItem("noti3", NotificationType.Follow, xia, "关注了你", "1 小时"),
        NotificationItem("noti4", NotificationType.Renote, lin, "转发了你的帖子", "昨天", "n3"),
    )
}
