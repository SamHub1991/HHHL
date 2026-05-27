package cc.hhhl.client.model

private const val FLUENT_EMOJI_BASE_URL = "https://dc.hhhl.cc/fluent-emoji"

val achievementCatalog: List<Achievement> = listOf(
    achievement("notes1", "第一篇帖子", "发布第一篇帖子。", "1f4dd.png", AchievementRank.Bronze),
    achievement("notes10", "稳定输出", "发布 10 篇帖子。", "1f4dd.png", AchievementRank.Bronze),
    achievement("notes100", "高产创作者", "发布 100 篇帖子。", "1f4da.png", AchievementRank.Silver),
    achievement("login3", "常来看看", "累计登录 3 天。", "1f44b.png", AchievementRank.Bronze),
    achievement("login7", "一周朋友", "累计登录 7 天。", "1f4c5.png", AchievementRank.Silver),
    achievement("login15", "半月同行", "累计登录 15 天。", "1f4c6.png", AchievementRank.Silver),
    achievement("login30", "月度常客", "累计登录 30 天。", "1f3c5.png", AchievementRank.Gold),
    achievement("followers1", "第一位关注者", "获得第一位关注者。", "1f91d.png", AchievementRank.Bronze),
    achievement("followers10", "开始热闹", "获得 10 位关注者。", "1f465.png", AchievementRank.Bronze),
    achievement("followers50", "小有名气", "获得 50 位关注者。", "1f31f.png", AchievementRank.Silver),
    achievement("followers100", "百人关注", "获得 100 位关注者。", "1f4af.png", AchievementRank.Gold),
    achievement("followers300", "广受关注", "获得 300 位关注者。", "1f451.png", AchievementRank.Gold),
    achievement("followers500", "影响力", "获得 500 位关注者。", "1f451.png", AchievementRank.Platinum),
    achievement("followers1000", "千人关注", "获得 1000 位关注者。", "1f451.png", AchievementRank.Platinum),
    achievement("following1", "主动连接", "关注第一位用户。", "1f517.png", AchievementRank.Bronze),
    achievement("following10", "兴趣网络", "关注 10 位用户。", "1f310.png", AchievementRank.Bronze),
    achievement("following50", "信息流成形", "关注 50 位用户。", "1f5de-fe0f.png", AchievementRank.Silver),
    achievement("following100", "社交地图", "关注 100 位用户。", "1f5fa-fe0f.png", AchievementRank.Gold),
    achievement("following300", "大型网络", "关注 300 位用户。", "1f30c.png", AchievementRank.Gold),
    achievement("collectAchievements30", "成就收藏家", "解锁 30 个成就。", "1f3c5.png", AchievementRank.Silver),
    achievement("viewAchievements3min", "盯着奖杯", "在成就页停留一段时间。", "1f3c6.png", AchievementRank.Bronze),
    achievement("iLoveMisskey", "I love Misskey", "触发隐藏成就。", "2764-fe0f.png", AchievementRank.Silver),
    achievement("foundTreasure", "发现宝藏", "找到隐藏的宝藏。", "1f48e.png", AchievementRank.Gold),
    achievement("client30min", "沉浸体验", "连续使用客户端 30 分钟。", "23f1-fe0f.png", AchievementRank.Bronze),
    achievement("client60min", "深度使用", "连续使用客户端 60 分钟。", "23f3.png", AchievementRank.Silver),
    achievement("noteDeletedWithin1min", "快速撤回", "发帖后 1 分钟内删除。", "1f5d1-fe0f.png", AchievementRank.Bronze),
    achievement("postedAtLateNight", "夜猫子", "深夜发帖。", "1f319.png", AchievementRank.Bronze),
    achievement("postedAt0min0sec", "准点发布", "在整点整分整秒发帖。", "1f570-fe0f.png", AchievementRank.Silver),
    achievement("selfQuote", "引用自己", "引用自己的帖子。", "1fa9e.png", AchievementRank.Bronze),
    achievement("htl20npm", "时间线漫游", "浏览本地时间线。", "1f9ed.png", AchievementRank.Bronze),
    achievement("viewInstanceChart", "数据观察者", "查看实例图表。", "1f4ca.png", AchievementRank.Bronze),
    achievement("outputHelloWorldOnScratchpad", "Hello World", "在 Scratchpad 输出 Hello World。", "1f4bb.png", AchievementRank.Silver),
    achievement("open3windows", "多窗口", "同时打开多个窗口。", "1fa9f.png", AchievementRank.Bronze),
    achievement("driveFolderCircularReference", "文件夹悖论", "触发 Drive 文件夹循环引用保护。", "1f4c1.png", AchievementRank.Silver),
    achievement("reactWithoutRead", "快速反应", "未打开详情就做出反应。", "26a1.png", AchievementRank.Bronze),
    achievement("clickedClickHere", "点这里", "点击了那个入口。", "1f446.png", AchievementRank.Bronze),
    achievement("justPlainLucky", "纯粹幸运", "随机触发的幸运成就。", "1f340.png", AchievementRank.Platinum),
    achievement("setNameToSyuilo", "Syuilo", "把名字设置为 Syuilo。", "1f431.png", AchievementRank.Silver),
    achievement("cookieClicked", "点击饼干", "点击了饼干。", "1f36a.png", AchievementRank.Bronze),
    achievement("brainDiver", "Brain Diver", "触发隐藏彩蛋。", "1f9e0.png", AchievementRank.Gold),
    achievement("smashTestNotificationButton", "测试通知", "多次触发测试通知。", "1f514.png", AchievementRank.Bronze),
    achievement("tutorialCompleted", "完成教程", "完成新手教程。", "1f393.png", AchievementRank.Bronze),
    achievement("bubbleGameExplodingHead", "爆炸头", "泡泡游戏隐藏成就。", "1f92f.png", AchievementRank.Silver),
    achievement("bubbleGameDoubleExplodingHead", "双重爆炸头", "泡泡游戏隐藏成就。", "1f92f.png", AchievementRank.Silver),
)

fun achievementMetadata(name: String): Achievement {
    return achievementCatalog.firstOrNull { it.name == name }
        ?: achievement(name, name, "已从服务器获取，但本地暂未内置说明。", "1f3c6.png", AchievementRank.Bronze)
}

private fun achievement(
    name: String,
    title: String,
    description: String,
    icon: String,
    rank: AchievementRank,
    flavor: String = "",
): Achievement {
    return Achievement(
        name = name,
        title = title,
        description = description,
        flavor = flavor,
        rank = rank,
        iconUrl = "$FLUENT_EMOJI_BASE_URL/$icon",
    )
}
