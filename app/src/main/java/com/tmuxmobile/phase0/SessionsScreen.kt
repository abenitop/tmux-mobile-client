package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmuxmobile.phase0.ui.theme.Amber
import com.tmuxmobile.phase0.ui.theme.InkMuted
import com.tmuxmobile.phase0.ui.theme.InkText
import com.tmuxmobile.phase0.ui.theme.SurfaceWarm
import com.tmuxmobile.phase0.ui.theme.StatusGreen
import com.tmuxmobile.phase0.ui.theme.TerminalGreen

/**
 * Sessions list (home, design s1): the server's tmux sessions, tap to attach. Presentational
 * only — the caller owns connect/list/attach state so the screen can show loading, an error,
 * or the list without re-connecting.
 */
@Composable
fun SessionsScreen(
    sessions: List<TmuxSession>,
    loading: Boolean,
    error: String?,
    hostLabel: String,
    onAttach: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                hostLabel,
                color = InkMuted,
                fontFamily = JetBrainsMono,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sessions", color = InkText, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                if (!loading) {
                    Text(
                        if (sessions.isEmpty()) "0 sessions" else "${sessions.size} sessions",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TerminalGreen)
            }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error, color = InkMuted, style = MaterialTheme.typography.bodyLarge)
            }
            sessions.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No tmux sessions on this host.", color = InkMuted, style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(sessions, key = { it.name }) { s ->
                    SessionCard(s, nowSeconds = System.currentTimeMillis() / 1000) {
                        onAttach(s.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: TmuxSession, nowSeconds: Long, onAttach: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(SurfaceWarm, RoundedCornerShape(16.dp))
            .clickable(onClick = onAttach)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live dot: green when a client is attached, muted otherwise.
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (session.attached) StatusGreen else InkMuted, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(session.name, color = InkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (session.process != null) {
                Text(
                    session.process,
                    color = InkText,
                    fontFamily = JetBrainsMono,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .background(Amber, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${session.windows} window${if (session.windows == 1) "" else "s"} · ${SessionListParser.formatActivity(session.activity, nowSeconds)}",
            color = InkMuted,
            fontFamily = JetBrainsMono,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
