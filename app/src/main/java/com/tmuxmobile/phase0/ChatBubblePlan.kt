package com.tmuxmobile.phase0

/** Which side of the chat a bubble sits on. */
internal enum class BubbleSide { Mine, Theirs }

/**
 * Everything the bubble list needs to know about one row, computed once outside Compose
 * so it is unit-testable (see ChatBubblePlanTest).
 *
 * @param gapAboveDp vertical space before this row -- tight inside a run of same-side
 *   messages, loose when the sender changes, which is what makes a run read as a block.
 * @param showTail only the last bubble of a run gets the tail.
 */
internal data class BubbleRow(
    val event: ChatEvent,
    val side: BubbleSide,
    val showTail: Boolean,
    val gapAboveDp: Int,
)

private const val GAP_SAME_SENDER_DP = 2
private const val GAP_NEW_SENDER_DP = 8

/**
 * Groups a flat event list into WhatsApp-style runs: consecutive messages from the same
 * side are tight together and only the last one is tailed.
 */
internal fun chatBubblePlan(events: List<ChatEvent>): List<BubbleRow> =
    events.mapIndexed { index, event ->
        val side = sideOf(event)
        // A run ends here if this is the last event or the next one comes from elsewhere.
        val lastOfRun = events.getOrNull(index + 1)?.let { sideOf(it) } != side
        BubbleRow(
            event = event,
            side = side,
            showTail = lastOfRun,
            gapAboveDp = when {
                index == 0 -> 0
                sideOf(events[index - 1]) == side -> GAP_SAME_SENDER_DP
                else -> GAP_NEW_SENDER_DP
            },
        )
    }

private fun sideOf(event: ChatEvent): BubbleSide = when (event) {
    is ChatEvent.UserMessage -> BubbleSide.Mine
    is ChatEvent.AssistantMessage,
    is ChatEvent.ToolCallChip,
    is ChatEvent.DiffCard,
    is ChatEvent.PermissionPrompt,
    -> BubbleSide.Theirs
}
