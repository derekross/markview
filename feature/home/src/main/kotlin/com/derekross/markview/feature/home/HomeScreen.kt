package com.derekross.markview.feature.home

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.FolderEntry
import com.derekross.markview.core.data.LibraryEntry
import com.derekross.markview.core.designsystem.ProgressRing
import com.derekross.markview.core.designsystem.ReaderSettingsPanel
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onOpenDocument: (DocumentSource) -> Unit,
    onOpenFolder: (FolderEntry) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onOpenDocument(viewModel.sourceForPickedFile(uri))
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addFolder(uri, onOpenFolder)
    }
    val actions = HomeActions(
        openFile = { openFile.launch(MARKDOWN_MIME_TYPES) },
        openFolder = { openFolder.launch(null) },
        paste = {
            scope.launch {
                val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(context)?.toString()
                if (text.isNullOrBlank()) snackbar.showSnackbar("Clipboard is empty") else onOpenDocument(viewModel.sourceForPastedText(text))
            }
        },
        openUrl = { showUrlDialog = true },
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Markview") },
                subtitle = { Text(greeting()) },
                actions = {
                    IconButton(onClick = { showAppearance = true }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Appearance")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(checked = fabExpanded, onCheckedChange = { fabExpanded = it }) {
                        Icon(
                            if (checkedProgress > 0.5f) Icons.Outlined.Close else Icons.Outlined.Add,
                            contentDescription = if (fabExpanded) "Close menu" else "Open a document",
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                listOf(
                    Triple("Open file", Icons.Outlined.Description, actions.openFile),
                    Triple("Open folder", Icons.Outlined.CreateNewFolder, actions.openFolder),
                    Triple("Paste", Icons.Outlined.ContentPaste, actions.paste),
                    Triple("From URL", Icons.Outlined.Link, actions.openUrl),
                ).forEach { (label, icon, action) ->
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabExpanded = false
                            action()
                        },
                        icon = { Icon(icon, contentDescription = null) },
                        text = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        val lib = library
        when {
            lib == null -> Box(Modifier.fillMaxSize().padding(padding))
            lib.entries.isEmpty() && lib.folders.isEmpty() -> EmptyState(actions, Modifier.padding(padding))
            else -> LibraryContent(
                recents = lib.recents,
                folders = lib.folders,
                padding = padding,
                onOpen = { onOpenDocument(it.source) },
                onOpenFolder = onOpenFolder,
                onRemoveFolder = viewModel::removeFolder,
                onToggleFavorite = { viewModel.toggleFavorite(it.key, !it.favorite) },
                onRemove = { viewModel.remove(it.key) },
            )
        }
    }

    if (showUrlDialog) {
        OpenUrlDialog(
            onDismiss = { showUrlDialog = false },
            onOpen = {
                showUrlDialog = false
                onOpenDocument(viewModel.sourceForUrl(it))
            },
        )
    }
    if (showAppearance) {
        ModalBottomSheet(onDismissRequest = { showAppearance = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ReaderSettingsPanel(settings, viewModel::updateSettings)
        }
    }
}

private class HomeActions(
    val openFile: () -> Unit,
    val openFolder: () -> Unit,
    val paste: () -> Unit,
    val openUrl: () -> Unit,
)

internal val MARKDOWN_MIME_TYPES = arrayOf(
    "text/markdown", "text/x-markdown", "text/plain", "text/*", "application/octet-stream",
)

private fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late-night reading"
}

@Composable
private fun LibraryContent(
    recents: List<LibraryEntry>,
    folders: List<FolderEntry>,
    padding: PaddingValues,
    onOpen: (LibraryEntry) -> Unit,
    onOpenFolder: (FolderEntry) -> Unit,
    onRemoveFolder: (FolderEntry) -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
) {
    val inProgress = recents.filter { it.progress in 0.02f..0.97f }.take(10)
    val favorites = recents.filter { it.favorite }
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (inProgress.isNotEmpty()) {
            item(key = "continue-header") { SectionHeader("Continue reading") }
            item(key = "continue") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(inProgress, key = { it.key }) { entry -> ContinueCard(entry) { onOpen(entry) } }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "fav-header") { SectionHeader("Favorites") }
            items(favorites, key = { "fav-" + it.key }) { entry ->
                DocumentRow(entry, onClick = { onOpen(entry) }, onToggleFavorite = { onToggleFavorite(entry) }, onRemove = { onRemove(entry) })
            }
        }
        if (folders.isNotEmpty()) {
            item(key = "folders-header") { SectionHeader("Folders") }
            items(folders, key = { "folder-" + it.treeUri }) { folder ->
                FolderRow(folder, onClick = { onOpenFolder(folder) }, onRemove = { onRemoveFolder(folder) })
            }
        }
        if (recents.isNotEmpty()) {
            item(key = "recent-header") { SectionHeader("Recent") }
            items(recents, key = { "recent-" + it.key }) { entry ->
                DocumentRow(entry, onClick = { onOpen(entry) }, onToggleFavorite = { onToggleFavorite(entry) }, onRemove = { onRemove(entry) })
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ContinueCard(entry: LibraryEntry, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val minutesLeft = (entry.readingMinutes * (1f - entry.progress)).toInt().coerceAtLeast(1)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        modifier = Modifier.width(264.dp).height(164.dp),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer.copy(alpha = 0.7f))),
            ),
        ) {
            Column(Modifier.padding(20.dp).fillMaxSize()) {
                ProgressRing(
                    progress = entry.progress,
                    color = colors.onPrimaryContainer,
                    track = colors.onPrimaryContainer.copy(alpha = 0.15f),
                    size = 36.dp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$minutesLeft min left",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun LeadingTile(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun DocumentRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val remote = entry.source is DocumentSource.Remote
    val subtitle = buildList {
        add(if (remote) entry.key.substringAfter("://").substringBefore('/') else entry.displayName)
        add(relativeTime(entry.lastOpened))
        if (entry.readingMinutes > 0) add("${entry.readingMinutes} min")
    }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingTile(if (remote) Icons.Outlined.Public else Icons.AutoMirrored.Outlined.Article)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            entry.progress >= 0.97f -> Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Finished",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp).size(26.dp),
            )
            entry.progress > 0.02f ->
                ProgressRing(entry.progress, size = 26.dp, stroke = 3.dp, showLabel = false, modifier = Modifier.padding(horizontal = 8.dp))
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (entry.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                contentDescription = if (entry.favorite) "Remove from favorites" else "Add to favorites",
                tint = if (entry.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove from history") }, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingTile(Icons.Outlined.Folder)
        Spacer(Modifier.width(16.dp))
        Text(folder.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove folder") }, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

@Composable
private fun EmptyState(actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(40.dp))
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
                .semantics { contentDescription = "Markview" },
            contentAlignment = Alignment.Center,
        ) {
            Text("M↓", style = MaterialTheme.typography.displayMedium, color = colors.onPrimaryContainer)
        }
        Spacer(Modifier.height(28.dp))
        Text("A calmer way to read Markdown", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open a file, add a folder of notes, or paste a link to any README. Your place is remembered automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = actions.openFile, modifier = Modifier.widthIn(min = 220.dp)) {
            Icon(Icons.Outlined.Description, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open a file")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.openFolder, modifier = Modifier.widthIn(min = 220.dp)) {
            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add a folder")
        }
        Row(Modifier.padding(top = 8.dp)) {
            TextButton(onClick = actions.openUrl) { Text("Open a URL") }
            TextButton(onClick = actions.paste) { Text("Paste") }
        }
    }
}
