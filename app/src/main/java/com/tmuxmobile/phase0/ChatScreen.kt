package com.tmuxmobile.phase0

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(events: List<ChatEvent>, onSend: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ponytail: no auto-scroll on new events yet -- LazyColumn stays where the user
        // put it. Add when someone actually complains about missing the live tail.
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(events) { event -> ChatBubble(event) }
        }
        ComposeInputBar(onSend = onSend)
    }
}

@Composable
private fun ChatBubble(event: ChatEvent) {
    when (event) {
        is ChatEvent.UserMessage -> Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Card { Text(event.text, modifier = Modifier.padding(8.dp)) }
        }
        is ChatEvent.AssistantMessage -> Text(
            event.text,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        is ChatEvent.ToolCallChip -> Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            Text(
                "${event.name}: ${event.summary}",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
