package com.tmuxmobile.phase0

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tmuxmobile.phase0.ui.theme.Amber
import com.tmuxmobile.phase0.ui.theme.StatusGreen
import kotlinx.coroutines.launch

/**
 * App-level chrome: hamburger drawer + top app bar + 3-dot overflow, composed AROUND the
 * NavHost so it is present on every destination. This is the fix for "can't see the new
 * UI": the previous TerminalChrome lived inside SpikeScreen (terminal only), so the Hosts
 * launch screen had no chrome at all.
 *
 * All state (title, grey-out, callbacks) is read from [AppState], the shared holder, so
 * this file stays pure presentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChrome(
    appState: AppState,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var overflowOpen by remember { mutableStateOf(false) }
    // Submenu state for the overflow: null = main menu; "font"/"dense"/"toolbar" = a
    // submenu replacing the menu contents (a back item returns). Nested DropdownMenus are
    // fragile to position; swapping the content is robust.
    var overflowSubmenu by remember { mutableStateOf<String?>(null) }
    var downloadPath by remember { mutableStateOf("") }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "hosts"

    val (title, subtitle) = titleFor(route, backStackEntry?.arguments?.getLong("hostId") ?: 0L, appState)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                DrawerSectionLabel("SSH connect")
                DrawerEntry(Icons.AutoMirrored.Filled.List, "Saved hosts", enabled = true) {
                    scope.launch { drawerState.close() }
                    navController.navigate("hosts") { popUpTo("hosts") { inclusive = true } }
                }
                DrawerEntry(Icons.Filled.Add, "New host", enabled = true) {
                    scope.launch { drawerState.close() }
                    navController.navigate("host_form")
                }
                DrawerSectionLabel("TMUX")
                DrawerEntry(Icons.Filled.Terminal, "Tmux sessions", enabled = !appState.hostsEmpty) {
                    scope.launch { drawerState.close() }
                    navController.navigate("host_picker")
                }
                DrawerSectionLabel("View")
                DrawerEntry(Icons.Filled.Terminal, "Terminal", enabled = appState.connected) {
                    scope.launch { drawerState.close() }
                    appState.viewMode = "terminal"
                }
                DrawerEntry(Icons.AutoMirrored.Filled.Chat, "Chat: Claude", enabled = appState.connected) {
                    scope.launch { drawerState.close() }
                    appState.viewMode = "chat-claude"
                }
                DrawerEntry(Icons.AutoMirrored.Filled.Chat, "Chat: Hermes", enabled = appState.connected) {
                    scope.launch { drawerState.close() }
                    appState.viewMode = "chat-hermes"
                }
                DrawerSectionLabel("Input mode")
                DrawerEntry(Icons.Filled.Keyboard, "Compose", enabled = appState.connected) {
                    scope.launch { drawerState.close() }
                    appState.inputMode = "compose"
                }
                DrawerEntry(Icons.Filled.Terminal, "Raw", enabled = appState.connected) {
                    scope.launch { drawerState.close() }
                    appState.inputMode = "raw"
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(2.dp))
                            if (appState.connected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(StatusGreen, CircleShape),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (appState.connected) {
                        IconButton(onClick = { appState.readOnly = !appState.readOnly }) {
                            Icon(
                                imageVector = if (appState.readOnly) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = if (appState.readOnly) "Read-only" else "Typing",
                                tint = if (appState.readOnly) MaterialTheme.colorScheme.onSurfaceVariant else Amber,
                            )
                        }
                    }
                    IconButton(onClick = { overflowOpen = true; overflowSubmenu = null }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false; overflowSubmenu = null },
                    ) {
                        when (overflowSubmenu) {
                            null -> OverflowMain(
                                appState,
                                onSubmenu = { overflowSubmenu = it },
                                onClose = { overflowOpen = false },
                            )
                            "font" -> OverflowFontSize(appState) { overflowSubmenu = null }
                            "dense" -> OverflowDense(appState) { overflowSubmenu = null }
                            "toolbar" -> OverflowToolbar(appState) { overflowSubmenu = null }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            content()
        }
    }

    if (appState.showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { appState.showDownloadDialog = false },
            title = { Text("Download file") },
            text = {
                OutlinedTextField(
                    value = downloadPath,
                    onValueChange = { downloadPath = it },
                    label = { Text("Remote path") },
                    placeholder = { Text("/home/user/file.txt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = downloadPath.isNotBlank(),
                    onClick = {
                        val path = downloadPath.trim()
                        downloadPath = ""
                        appState.showDownloadDialog = false
                        appState.downloadFile?.invoke(path)
                    },
                ) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { appState.showDownloadDialog = false; downloadPath = "" }) { Text("Cancel") }
            },
        )
    }
}

private fun titleFor(route: String, hostId: Long, appState: AppState): Pair<String, String> = when {
    route.startsWith("host_form") -> if (hostId == 0L) "New host" to "" else "Edit host" to ""
    route.startsWith("session") -> appState.attachedSessionName?.let { it to "attached" } ?: ("Sessions" to "")
    else -> "Hosts" to "servers"
}

/** Overflow main menu: the operator's six items plus the retained red Forget host. */
@Composable
private fun OverflowMain(appState: AppState, onSubmenu: (String) -> Unit, onClose: () -> Unit) {
    OverflowItem(Icons.Filled.Splitscreen, "Split right", enabled = appState.connected) {
        onClose(); appState.splitRight?.invoke()
    }
    OverflowItem(Icons.Filled.Splitscreen, "Split down", enabled = appState.connected) {
        onClose(); appState.splitDown?.invoke()
    }
    OverflowItem(Icons.Filled.FontDownload, "Font size", enabled = true) { onSubmenu("font") }
    OverflowItem(Icons.Filled.Download, "Download file", enabled = appState.connected) {
        onClose(); appState.showDownloadDialog = true
    }
    OverflowItem(Icons.Filled.Menu, "Dense top bar", enabled = true) { onSubmenu("dense") }
    OverflowItem(Icons.Filled.Keyboard, "Keyboard toolbar", enabled = true) { onSubmenu("toolbar") }
    OverflowItem(Icons.Filled.FormatClear, "Clear", enabled = appState.connected) {
        onClose(); appState.clearAll?.invoke()
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Forget host", color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
        onClick = { onClose(); appState.forgetHost?.invoke() },
        enabled = appState.connected,
    )
}

@Composable
private fun OverflowFontSize(appState: AppState, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Font size", fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = onBack,
    )
    AppPrefs.FONT_SIZES.forEach { size ->
        DropdownMenuItem(
            text = { Text("$size", fontWeight = if (size == appState.prefs.fontSize) FontWeight.Bold else FontWeight.Normal) },
            trailingIcon = { if (size == appState.prefs.fontSize) Icon(Icons.Filled.Check, null, tint = Amber) },
            onClick = { appState.prefs.fontSize = size },
        )
    }
}

@Composable
private fun OverflowDense(appState: AppState, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Dense top bar", fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = onBack,
    )
    val modes = listOf("auto" to "Auto (landscape)", "always" to "Always", "never" to "Never")
    modes.forEach { (value, label) ->
        DropdownMenuItem(
            text = { Text(label, fontWeight = if (value == appState.prefs.denseBar) FontWeight.Bold else FontWeight.Normal) },
            trailingIcon = { if (value == appState.prefs.denseBar) Icon(Icons.Filled.Check, null, tint = Amber) },
            onClick = { appState.prefs.denseBar = value },
        )
    }
}

@Composable
private fun OverflowToolbar(appState: AppState, onBack: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Keyboard toolbar", fontWeight = FontWeight.Bold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
        onClick = onBack,
    )
    val modes = listOf("auto" to "Auto (with keyboard)", "always" to "Always", "never" to "Never")
    modes.forEach { (value, label) ->
        DropdownMenuItem(
            text = { Text(label, fontWeight = if (value == appState.prefs.keyboardToolbar) FontWeight.Bold else FontWeight.Normal) },
            trailingIcon = { if (value == appState.prefs.keyboardToolbar) Icon(Icons.Filled.Check, null, tint = Amber) },
            onClick = { appState.prefs.keyboardToolbar = value },
        )
    }
}

@Composable
private fun OverflowItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        leadingIcon = { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        onClick = { if (enabled) onClick() },
        enabled = enabled,
    )
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = JetBrainsMono,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerEntry(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        selected = false,
        icon = { Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun DrawerHeader() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFF005040), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("⌥", color = StatusGreen, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "tmux mobile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "cockpit, not workstation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
