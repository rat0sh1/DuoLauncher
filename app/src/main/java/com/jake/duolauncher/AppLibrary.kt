@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
    onUnlockPrivate: (Long) -> Unit = onTurnOnWork,
    onLockPrivate: (Long) -> Unit = {},
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val personalProfile = state.profiles.firstOrNull { it.isPersonal }
    val workProfile = state.profiles.firstOrNull { it.isWork }
    val privateProfile = state.profiles.firstOrNull { it.isPrivate }

    val hasWork = workProfile != null || state.apps.any { it.isWork }
    val hasPrivate = privateProfile != null || state.apps.any { it.isPrivate }
    val isPrivateUnlocked = privateProfile != null && !privateProfile.quiet && privateProfile.unlocked

    var activeTab by remember { mutableStateOf("personal") }
    var wasPrivateUnlocked by remember { mutableStateOf(isPrivateUnlocked) }

    LaunchedEffect(isPrivateUnlocked) {
        if (!isPrivateUnlocked && activeTab == "private") {
            activeTab = "personal"
        } else if (isPrivateUnlocked && !wasPrivateUnlocked) {
            activeTab = "private"
            onQuery("")
        }
        wasPrivateUnlocked = isPrivateUnlocked
    }

    val selectedProfile = when (activeTab) {
        "work" -> workProfile
        "private" -> privateProfile
        else -> personalProfile
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeTab, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }

    val showTabs = hasWork || (hasPrivate && isPrivateUnlocked)

    val visibleApps = remember(state.apps, query, activeTab, showTabs, hasWork, hasPrivate, isPrivateUnlocked) {
        state.apps.filter { app ->
            val matchesProfile = when {
                !showTabs -> !app.isWork && !app.isPrivate
                activeTab == "work" -> app.isWork
                activeTab == "private" -> app.isPrivate
                else -> !app.isWork && !app.isPrivate
            }
            matchesProfile && app.label.contains(query.trim(), true)
        }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }

    val isPrivateLocked = privateProfile != null && (privateProfile.quiet || !privateProfile.unlocked)
    val trimmedQuery = query.trim().lowercase()
    val isSearchingForPrivate = isPrivateLocked && trimmedQuery.length >= 2 && (
        "private".contains(trimmedQuery) ||
        "privado".contains(trimmedQuery) ||
        "espacio privado".contains(trimmedQuery) ||
        "carpeta privada".contains(trimmedQuery) ||
        "private space".contains(trimmedQuery) ||
        trimmedQuery.contains("priv") ||
        trimmedQuery.contains("secr") ||
        trimmedQuery.contains("candado")
    )

    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing) "Choose home apps" else "All apps", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                Text(if (editing) "${pinned.size} pinned" else "${visibleApps.size}", color = ink, fontSize = 12.sp)
            }
            if (showTabs) Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeTab == "personal",
                    onClick = { activeTab = "personal" },
                    label = { Text("Personal") }
                )
                if (hasWork) {
                    FilterChip(
                        selected = activeTab == "work",
                        onClick = { activeTab = "work" },
                        label = { Text("Work") }
                    )
                }
                if (hasPrivate && isPrivateUnlocked) {
                    FilterChip(
                        selected = activeTab == "private",
                        onClick = { activeTab = "private" },
                        label = { Text("Privado") }
                    )
                    Spacer(Modifier.weight(1f))
                    if (activeTab == "private" && privateProfile != null) {
                        IconButton(
                            onClick = { onLockPrivate(privateProfile.userSerial) },
                            modifier = Modifier.size(32.dp).testTag("lock-private-space")
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = "Bloquear espacio privado",
                                tint = ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(if (editing) "pin-search" else "library-search"),
                placeholder = { Text("Search apps") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } },
                colors = if (glass) OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ink, unfocusedTextColor = ink, cursorColor = ink,
                    focusedContainerColor = Color.White.copy(alpha = .18f), unfocusedContainerColor = Color.White.copy(alpha = .12f),
                    focusedBorderColor = Color.White.copy(alpha = .8f), unfocusedBorderColor = Color.White.copy(alpha = .45f),
                    focusedPlaceholderColor = ink, unfocusedPlaceholderColor = ink,
                    focusedLeadingIconColor = ink, unfocusedLeadingIconColor = ink,
                    focusedTrailingIconColor = ink, unfocusedTrailingIconColor = ink,
                ) else OutlinedTextFieldDefaults.colors())
            LazyColumn(Modifier.weight(1f).testTag("all-apps-list"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                if (isSearchingForPrivate && privateProfile != null) item("unlock-private-space") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onUnlockPrivate(privateProfile.userSerial) }
                            .testTag("unlock-private-item"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (glass) (if (palette.dark) Color(0xFF2C3E50) else Color(0xFFE8EEF5))
                                else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (glass) ink else MaterialTheme.colorScheme.onPrimaryContainer,
                        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .3f)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (glass) ink else MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp)
                            ) {
                                Text(
                                    "Espacio privado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Bloqueado · Toca para desbloquear",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ink.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
                if (activeTab == "work" && selectedProfile?.available == false) item("work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "Work apps are paused" else "Work profile is unavailable")
                        if (selectedProfile.quiet) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text("Turn on work apps") }
                    }
                }
                if (activeTab == "private" && selectedProfile?.available == false) item("private-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Espacio privado bloqueado")
                        Button(onClick = { onUnlockPrivate(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("unlock-private-button")) { Text("Desbloquear") }
                    }
                }
                if (groups.isEmpty()) item { Text(if (state.loading) "Loading apps…" else "No apps found", Modifier.padding(vertical = 20.dp)) }
                groups.forEach { (letter, entries) ->
                    stickyHeader(key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // An opaque small chip prevents text from showing through the sticky letter.
                            Box(Modifier.size(width = 32.dp, height = 28.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragModifier).clip(RoundedCornerShape(14.dp)).testTag("library-app-${app.id}")
                            .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
                                else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon.asImageBitmap(), null, Modifier.size(40.dp)
                                .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(10.dp)))
                            Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, fontSize = 14.sp)
                            if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
                                Icon(if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                    if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
