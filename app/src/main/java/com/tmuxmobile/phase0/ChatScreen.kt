package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(events: List<ChatEvent>, onSend: (String) -> Unit) {
    val listState = rememberLazyListState()
    val rows = chatBubblePlan(events)

    // Follow the live tail -- scroll to the newest event whenever the list grows.
    //
    // NOT gated on "is the bottom currently visible": during the initial backlog fill the
    // list legitimately sits at the top while ~1800 items stream in, so a position-based
    // gate reads `false` and leaves the window pinned at the top forever (verified -- that
    // is exactly what "the chat window is stuck" looked like). Gated on the user's drag
    // instead, so an in-progress gesture is never fought; a deliberate scroll-up is
    // re-pinned on the next arrival, which is the trade-off of unconditionally following.
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(rows.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChatBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
        ) {
            // Keyed by index AND content. Content alone is NOT unique: this transcript is
            // a tool-call log, so identical entries recur constantly (a real session had
            // `{"output": "", "exit_code": 0, "error": null}` eight times, and 274
            // duplicate groups across 624 rows). Duplicate keys make LazyColumn throw
            // IllegalArgumentException. The list is append-only, so an item's index is
            // stable and unique -- combining the two gives both uniqueness and stability.
            itemsIndexed(rows, key = { index, row -> itemKey(index, row.event) }) { _, row ->
                Bubble(row)
            }
        }
        ComposeInputBar(onSend = onSend)
    }
}

/**
 * Stable, unique LazyColumn key for events[index].
 *
 * The index alone would be unique but not stable: an append-only list keeps indices
 * fixed, yet building the displayed list by CONCATENATING two sources (`Claude + Hermes`)
 * would shift every Claude index when a Claude item arrives -- re-keying the whole list
 * on each event and defeating the point of keys. Content alone is stable but not unique.
 * Together they are both.
 */
internal fun itemKey(index: Int, event: ChatEvent): String = "$index:${event.identity()}"

/**
 * Content-based identity. NOT unique on its own (see itemKey) -- it exists to keep a
 * key stable when an item's content is what moved, and reads well in a crash dump.
 */
private fun ChatEvent.identity(): String = when (this) {
    is ChatEvent.UserMessage -> "u:$text"
    is ChatEvent.AssistantMessage -> "a:$text"
    is ChatEvent.ToolCallChip -> "t:$name:$summary"
}

/** One bubble: tailed on the last message of a run, plain within it. */
@Composable
private fun Bubble(row: BubbleRow) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 14.dp.toPx() }
    val tailPx = with(density) { 6.dp.toPx() }
    val mine = row.side == BubbleSide.Mine
    val tailOnRight = mine

    val shape = if (row.showTail) {
        chatBubbleShape(radiusPx, tailPx, tailOnRight)
    } else {
        plainBubbleShape(radiusPx)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = row.gapAboveDp.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        // fillMaxWidth(0.78f) is the "don't span the whole screen" WhatsApp look; long
        // lines still wrap inside the bubble rather than stretching it edge to edge.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .background(
                    color = when {
                        row.event is ChatEvent.ToolCallChip -> BubbleToolChip
                        mine -> BubbleMine
                        else -> BubbleTheirs
                    },
                    shape = shape,
                )
                // The tail is carved out of the bubble's own bounds (see chatBubbleShape),
                // so the text is inset on that side to keep clear of it.
                .padding(
                    start = if (row.showTail && !tailOnRight) 14.dp else 10.dp,
                    end = if (row.showTail && tailOnRight) 14.dp else 10.dp,
                    top = 8.dp,
                    bottom = if (row.showTail) 10.dp else 8.dp,
                ),
        ) {
            when (val event = row.event) {
                is ChatEvent.UserMessage -> Text(event.text, color = BubbleText)
                is ChatEvent.AssistantMessage -> Text(
                    event.text,
                    color = BubbleText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is ChatEvent.ToolCallChip -> Text(
                    "${event.name}: ${event.summary}",
                    color = BubbleText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
