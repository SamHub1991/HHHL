package cc.hhhl.client.automation

import cc.hhhl.client.api.MainStreamingOptions
import cc.hhhl.client.api.defaultMainStreamingTimelineKinds

fun List<AutomationRule>.toMainStreamingOptions(channelLimit: Int): MainStreamingOptions {
    val channelIds = filter { rule -> rule.enabled && rule.trigger == AutomationTrigger.TimelineNote }
        .flatMap { rule ->
            rule.conditions
                .filter { condition ->
                    condition.enabled &&
                        condition.type == AutomationConditionType.ChannelId &&
                        condition.value.isNotBlank()
                }
                .flatMap { condition -> condition.value.splitAutomationValues() }
        }
        .distinct()
        .take(channelLimit.coerceAtLeast(0))
    return MainStreamingOptions(
        timelineKinds = defaultMainStreamingTimelineKinds,
        channelIds = channelIds,
    )
}

private fun String.splitAutomationValues(): List<String> {
    return split(',', '，', '\n', ';', '；', '|', '/', '、')
        .map { it.trim().trim('@') }
        .filter { it.isNotEmpty() }
}
