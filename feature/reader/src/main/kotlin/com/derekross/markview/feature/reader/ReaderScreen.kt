package com.derekross.markview.feature.reader

import android.app.Application
import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.core.data.LinkResolver
import com.derekross.markview.core.markdown.MdDocument
import com.derekross.markview.core.render.FootnotesSection
import com.derekross.markview.core.render.LocalMarkdownCallbacks
import com.derekross.markview.core.render.LocalMarkdownTheme
import com.derekross.markview.core.render.MarkdownBlock
import com.derekross.markview.core.render.MarkdownCallbacks
import com.derekross.markview.core.render.SearchHighlight
import com.derekross.markview.core.render.markdownBlocks
import com.derekross.markview.core.render.rememberMarkdownTheme
import com.derekross.markview.core.designsystem.ReaderSettingsPanel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Number of lazy items before the first Markdown block (the document header). */
private const val HEADER_ITEMS = 1

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderScreen(
    source: DocumentSource,
    onBack: () -> Unit,
    onOpenDocument: (DocumentSource) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel(key = source.key) {
        ReaderViewModel(context.applicationContext as Application, source)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorite by viewModel.favorite.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val listState = rememberLazyListState()

    var showToc by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var footnote by rememberSaveable { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<Pair<Any, String>?>(null) }

    val ready = state as? ReaderUiState.Ready
    val document = ready?.document ?: MdDocument.Empty

    // Keep the screen awake while reading, if the user asked for it.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    fun scrollToBlock(blockIndex: Int) {
        scope.launch { listState.animateScrollToItem(HEADER_ITEMS + blockIndex) }
    }

    val callbacks = remember(source, document) {
        MarkdownCallbacks(
            onLinkClick = { href ->
                when (val action = LinkRouter.route(source, href)) {
                    is LinkAction.ScrollToAnchor -> {
                        val index = document.blockIndexForAnchor(action.anchor)
                        if (index != null) scrollToBlock(index) else scope.launch { snackbar.showSnackbar("Section not found") }
                    }
                    is LinkAction.OpenDocument -> onOpenDocument(action.source)
                    is LinkAction.OpenExternal -> if (!context.openExternal(action.uri)) {
                        scope.launch { snackbar.showSnackbar("No app can open this link") }
                    }
                    LinkAction.Unresolvable -> scope.launch {
                        snackbar.showSnackbar("Can't follow relative links here. Open the containing folder to enable them.")
                    }
                }
            },
            onFootnoteClick = { footnote = it },
            onImageClick = { model, alt -> image = model to alt },
            resolveImage = { url -> LinkResolver.resolve(source, url) },
        )
    }

    // Restore the saved reading position once per load.
    LaunchedEffect(ready?.restore, ready != null) {
        val restore = ready?.restore ?: return@LaunchedEffect
        listState.scrollToItem(HEADER_ITEMS + restore.blockIndex.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0)), restore.offset)
    }

    val progress by remember(listState) { derivedStateOf { listState.readingProgress() } }
    val showScrollTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    // Persist position (debounced) as the user scrolls.
    @OptIn(FlowPreview::class)
    LaunchedEffect(listState, ready != null) {
        if (ready == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(400)
            .collect { (index, offset) ->
                viewModel.savePosition((index - HEADER_ITEMS).coerceAtLeast(0), if (index < HEADER_ITEMS) 0 else offset, listState.readingProgress())
            }
    }

    // Follow the current search match.
    LaunchedEffect(search.currentMatch) {
        search.currentMatch?.let { listState.animateScrollToItem(HEADER_ITEMS + it.blockIndex, -200) }
    }

    LaunchedEffect(ready?.revision) {
        if ((ready?.revision ?: 0) > 0) snackbar.showSnackbar("Updated from file")
    }

    val currentHeading by remember(document) {
        derivedStateOf { document.headingForBlock(listState.firstVisibleItemIndex - HEADER_ITEMS + 1) }
    }

    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection).nestedScroll(toolbarScroll),
        topBar = {
            AnimatedContent(search.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "topbar") { searching ->
                if (searching) {
                    FindBar(
                        state = search,
                        onQueryChange = viewModel::setQuery,
                        onNext = viewModel::nextMatch,
                        onPrevious = viewModel::previousMatch,
                        onClose = viewModel::closeSearch,
                    )
                } else {
                    Column {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(ready?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    currentHeading?.let { heading ->
                                        Text(
                                            heading.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                            },
                            actions = {
                                if (ready != null && source !is DocumentSource.Inline) {
                                    IconButton(onClick = viewModel::toggleFavorite) {
                                        Icon(
                                            if (favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                            contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (ready != null) {
                                    OverflowMenu(
                                        source = source,
                                        onCopy = {
                                            scope.launch {
                                                clipboard.setClipEntry(ClipData.newPlainText(ready.title, ready.rawText).toClipEntry())
                                                snackbar.showSnackbar("Markdown copied")
                                            }
                                        },
                                        onOpenWith = {
                                            if (source is DocumentSource.Local && !context.openWith(source)) {
                                                scope.launch { snackbar.showSnackbar("No other app can open this file") }
                                            }
                                        },
                                        onOpenInBrowser = { if (source is DocumentSource.Remote) context.openExternal(source.url) },
                                        onReload = viewModel::load,
                                    )
                                }
                            },
                            scrollBehavior = topBarScroll,
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        )
                        if (ready != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                trackColor = Color.Transparent,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val s = state) {
            ReaderUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
            is ReaderUiState.Error -> ErrorState(s.message, onRetry = viewModel::load, onBack = onBack, modifier = Modifier.padding(padding))
            is ReaderUiState.Ready -> {
                val theme = rememberMarkdownTheme(settings)
                CompositionLocalProvider(LocalMarkdownTheme provides theme, LocalMarkdownCallbacks provides callbacks) {
                    val highlight = remember(search) {
                        if (!search.active || search.query.isBlank()) {
                            SearchHighlight.None
                        } else {
                            SearchHighlight(search.query, search.currentMatch?.blockIndex ?: -1, search.currentMatch?.occurrence ?: -1)
                        }
                    }
                    DocumentBody(
                        document = s.document,
                        fallbackTitle = s.title,
                        listState = listState,
                        highlight = highlight,
                        maxWidth = settings.maxContentWidth,
                        padding = padding,
                    )
                }
                ReaderToolbar(
                    scrollBehavior = toolbarScroll,
                    showScrollTop = showScrollTop,
                    onToc = { showToc = true },
                    onSearch = viewModel::openSearch,
                    onAppearance = { showAppearance = true },
                    onShare = { context.shareDocument(source, s.title, s.rawText) },
                    onScrollTop = { scope.launch { listState.animateScrollToItem(0) } },
                    bottomInset = padding.calculateBottomPadding(),
                )
            }
        }
    }

    if (showToc && ready != null) {
        ModalBottomSheet(onDismissRequest = { showToc = false }, sheetState = rememberModalBottomSheetState()) {
            TableOfContents(
                document = document,
                currentHeading = currentHeading,
                onSelect = {
                    showToc = false
                    scrollToBlock(it.blockIndex)
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
    if (showAppearance) {
        ModalBottomSheet(
            onDismissRequest = { showAppearance = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            scrimColor = Color.Black.copy(alpha = 0.12f),
        ) {
            ReaderSettingsPanel(settings, viewModel::updateSettings)
        }
    }
    footnote?.let { label ->
        val note = document.footnotes[label]
        if (note != null) {
            ModalBottomSheet(onDismissRequest = { footnote = null }) {
                val theme = rememberMarkdownTheme(settings)
                CompositionLocalProvider(LocalMarkdownTheme provides theme, LocalMarkdownCallbacks provides callbacks) {
                    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                        Text("Footnote ${note.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        note.blocks.forEachIndexed { i, block ->
                            MarkdownBlock(block, -1, modifier = if (i == 0) Modifier else Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        } else {
            footnote = null
        }
    }
    image?.let { (model, alt) -> ImageViewer(model, alt) { image = null } }
}

@Composable
private fun DocumentBody(
    document: MdDocument,
    fallbackTitle: String,
    listState: LazyListState,
    highlight: SearchHighlight,
    maxWidth: Int,
    padding: PaddingValues,
) {
    val itemModifier = Modifier.widthIn(max = maxWidth.dp).fillMaxWidth().padding(horizontal = 22.dp)
    SelectionContainer {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 120.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { DocumentHeader(document, fallbackTitle, itemModifier) }
            markdownBlocks(document, highlight, itemModifier)
            if (document.footnotes.isNotEmpty()) {
                item(key = "footnotes") { FootnotesSection(document, itemModifier.padding(top = 40.dp)) }
            }
            item(key = "end") { EndOfDocument(itemModifier) }
        }
    }
}

@Composable
private fun EndOfDocument(modifier: Modifier) {
    Box(modifier.padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text("◆", color = MaterialTheme.colorScheme.outlineVariant, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReaderToolbar(
    scrollBehavior: androidx.compose.material3.FloatingToolbarScrollBehavior,
    showScrollTop: Boolean,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onAppearance: () -> Unit,
    onShare: () -> Unit,
    onScrollTop: () -> Unit,
    bottomInset: Dp,
) {
    // The exit-always behavior hides the toolbar by the distance from its top to the bottom of its
    // parent. Keep the parent full-screen and lift the toolbar above the navigation bar with an
    // offset (not parent padding) so that distance includes the inset and it fully leaves the screen.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        HorizontalFloatingToolbar(
            expanded = true,
            scrollBehavior = scrollBehavior,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier.offset(y = -(FloatingToolbarDefaults.ScreenOffset + bottomInset)),
        ) {
            IconButton(onClick = onToc) { Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Table of contents") }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Find in document") }
            IconButton(onClick = onAppearance) { Icon(Icons.Outlined.FormatSize, contentDescription = "Reading appearance") }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
            if (showScrollTop) {
                IconButton(onClick = onScrollTop) { Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Back to top") }
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    source: DocumentSource,
    onCopy: () -> Unit,
    onOpenWith: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onReload: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Copy Markdown") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = { open = false; onCopy() },
            )
            if (source is DocumentSource.Local) {
                DropdownMenuItem(
                    text = { Text("Open with…") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                    onClick = { open = false; onOpenWith() },
                )
            }
            if (source is DocumentSource.Remote) {
                DropdownMenuItem(
                    text = { Text("Open in browser") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                    onClick = { open = false; onOpenInBrowser() },
                )
            }
            if (source !is DocumentSource.Inline) {
                DropdownMenuItem(
                    text = { Text("Reload") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                    onClick = { open = false; onReload() },
                )
            }
        }
    }
}

@Composable
private fun FindBar(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search") }
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in document") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (state.query.isNotBlank()) {
                Text(
                    if (state.matches.isEmpty()) "0/0" else "${state.current + 1}/${state.matches.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPrevious, enabled = state.matches.isNotEmpty()) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = state.matches.isNotEmpty()) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Next match")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Couldn't open document", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Go back") }
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

/** 0..1 progress through the list; 1 when the end is fully visible. */
private fun LazyListState.readingProgress(): Float {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index == total - 1 && last.offset + last.size <= info.viewportEndOffset) return 1f
    val first = info.visibleItemsInfo.first()
    val fraction = if (first.size > 0) (-first.offset).coerceAtLeast(0).toFloat() / first.size else 0f
    return ((first.index + fraction) / total).coerceIn(0f, 1f)
}
