package com.derekross.markview.feature.reader

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.derekross.markview.core.render.engine.RenderEngineHost
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
    val speech by viewModel.readAloud.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val listState = rememberLazyListState()

    var showToc by rememberSaveable { mutableStateOf(false) }
    var focusMode by rememberSaveable { mutableStateOf(false) }
    /** On wide screens the contents live in a side panel that can be toggled. */
    var tocPanel by rememberSaveable { mutableStateOf(true) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var footnote by rememberSaveable { mutableStateOf<String?>(null) }
    var image by remember { mutableStateOf<Pair<Any, String>?>(null) }

    val ready = state as? ReaderUiState.Ready
    val document = ready?.document ?: MdDocument.Empty

    // Keep the screen awake while reading if asked, and always in focus mode or while reading aloud.
    val view = LocalView.current
    val keepOn = settings.keepScreenOn || focusMode || speech.active
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    ImmersiveMode(enabled = focusMode)

    BackHandler(enabled = focusMode) { focusMode = false }
    BackHandler(enabled = search.active && !focusMode) { viewModel.closeSearch() }

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

    /** The block under the reading line (40% down the viewport); dimmed focus mode keeps it lit. */
    val focusedBlock by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val line = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) * 0.4f
            info.visibleItemsInfo
                .firstOrNull { it.offset <= line && it.offset + it.size > line && (it.key as? String)?.startsWith("block-") == true }
                ?.let { it.index - HEADER_ITEMS } ?: -1
        }
    }

    // Read aloud: keep the spoken block comfortably in view.
    LaunchedEffect(speech.blockIndex, speech.status) {
        if (speech.status != ReadAloudState.Status.Playing || speech.blockIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == HEADER_ITEMS + speech.blockIndex }
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val comfortable = item != null && item.offset >= info.viewportStartOffset + viewport * 0.1f &&
            item.offset + minOf(item.size, viewport / 2) <= info.viewportStartOffset + viewport * 0.75f
        if (!comfortable) listState.animateScrollToItem(HEADER_ITEMS + speech.blockIndex, -(viewport * 0.2f).toInt())
    }
    LaunchedEffect(speech.error) {
        speech.error?.let {
            viewModel.readAloud.clearError()
            snackbar.showSnackbar(it)
        }
    }

    val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val blockModifier: @Composable (Int) -> Modifier = { index ->
        val dim = focusMode && focusedBlock >= 0 && index != focusedBlock
        val alpha by animateFloatAsState(if (dim) 0.16f else 1f, label = "focus")
        val reading = speech.active && index == speech.blockIndex
        val highlight by animateColorAsState(if (reading) highlightColor else highlightColor.copy(alpha = 0f), label = "speech")
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                if (highlight.alpha > 0f) {
                    val inset = 10.dp.toPx()
                    drawRoundRect(
                        color = highlight,
                        topLeft = Offset(-inset, -inset / 2),
                        size = Size(size.width + inset * 2, size.height + inset),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }
            }
    }

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
            if (!focusMode) AnimatedContent(search.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "topbar") { searching ->
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
                                        onShare = { context.shareDocument(source, ready.title, ready.rawText) },
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
                RenderEngineHost()
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 840.dp && s.document.headings.isNotEmpty()
                    Row(Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = wide && tocPanel && !focusMode,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(320.dp).fillMaxHeight()) {
                                TableOfContents(
                                    document = s.document,
                                    currentHeading = currentHeading,
                                    onSelect = { scrollToBlock(it.blockIndex) },
                                    modifier = Modifier.padding(top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding()),
                                )
                            }
                        }
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
                                blockModifier = blockModifier,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val firstVisibleBlock = { (listState.firstVisibleItemIndex - HEADER_ITEMS).coerceAtLeast(0) }
                    AnimatedContent(
                        targetState = when {
                            focusMode -> BottomChrome.None
                            speech.active -> BottomChrome.Player
                            else -> BottomChrome.Toolbar
                        },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "bottomChrome",
                        // Center the toolbar over the reading column, not the whole screen.
                        modifier = Modifier.fillMaxSize().padding(start = if (wide && tocPanel && !focusMode) 320.dp else 0.dp),
                    ) { chrome ->
                        when (chrome) {
                            BottomChrome.Toolbar -> ReaderToolbar(
                                scrollBehavior = toolbarScroll,
                                showScrollTop = showScrollTop,
                                tocActive = wide && tocPanel,
                                onToc = { if (wide) tocPanel = !tocPanel else showToc = true },
                                onSearch = viewModel::openSearch,
                                onAppearance = { showAppearance = true },
                                onReadAloud = { viewModel.readAloud.play(firstVisibleBlock()) },
                                onFocus = {
                                    viewModel.closeSearch()
                                    focusMode = true
                                },
                                onScrollTop = { scope.launch { listState.animateScrollToItem(0) } },
                                bottomInset = padding.calculateBottomPadding(),
                            )
                            BottomChrome.Player -> ReadAloudBar(
                                state = speech,
                                onTogglePause = viewModel.readAloud::togglePause,
                                onSkip = viewModel.readAloud::skip,
                                onRate = viewModel.readAloud::setRate,
                                onStop = viewModel.readAloud::stop,
                                bottomInset = padding.calculateBottomPadding(),
                            )
                            BottomChrome.None -> Box(Modifier.fillMaxSize())
                        }
                    }
                    if (focusMode) FocusExitButton(onExit = { focusMode = false })
                }
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
    blockModifier: @Composable (Int) -> Modifier,
    modifier: Modifier = Modifier,
) {
    val itemModifier = Modifier.widthIn(max = maxWidth.dp).fillMaxWidth().padding(horizontal = 22.dp)
    SelectionContainer(modifier) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 120.dp),
            modifier = Modifier.fillMaxSize().testTag("document"),
        ) {
            item(key = "header") { DocumentHeader(document, fallbackTitle, itemModifier) }
            markdownBlocks(document, highlight, itemModifier, blockModifier)
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

private enum class BottomChrome { Toolbar, Player, None }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReaderToolbar(
    scrollBehavior: FloatingToolbarScrollBehavior,
    showScrollTop: Boolean,
    tocActive: Boolean,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onAppearance: () -> Unit,
    onReadAloud: () -> Unit,
    onFocus: () -> Unit,
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
            IconToggleButton(checked = tocActive, onCheckedChange = { onToc() }) {
                Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Table of contents")
            }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Find in document") }
            IconButton(onClick = onAppearance) { Icon(Icons.Outlined.FormatSize, contentDescription = "Reading appearance") }
            IconButton(onClick = onReadAloud) { Icon(Icons.Outlined.Headphones, contentDescription = "Read aloud") }
            IconButton(onClick = onFocus) { Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Focus mode") }
            if (showScrollTop) {
                IconButton(onClick = onScrollTop) { Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Back to top") }
            }
        }
    }
}

/** Playback controls shown in place of the toolbar while reading aloud. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadAloudBar(
    state: ReadAloudState,
    onTogglePause: () -> Unit,
    onSkip: (forward: Boolean) -> Unit,
    onRate: (Float) -> Unit,
    onStop: () -> Unit,
    bottomInset: Dp,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier.offset(y = -(FloatingToolbarDefaults.ScreenOffset + bottomInset)),
        ) {
            IconButton(onClick = onStop) { Icon(Icons.Outlined.Close, contentDescription = "Stop reading aloud") }
            IconButton(onClick = { onSkip(false) }) { Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous paragraph") }
            FilledIconButton(onClick = onTogglePause, modifier = Modifier.size(52.dp)) {
                when (state.status) {
                    ReadAloudState.Status.Starting -> LoadingIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                    ReadAloudState.Status.Paused -> Icon(Icons.Outlined.PlayArrow, contentDescription = "Resume")
                    else -> Icon(Icons.Outlined.Pause, contentDescription = "Pause")
                }
            }
            IconButton(onClick = { onSkip(true) }) { Icon(Icons.Outlined.SkipNext, contentDescription = "Next paragraph") }
            TextButton(
                onClick = {
                    val rates = ReadAloudController.Rates
                    val next = rates[(rates.indexOfFirst { it >= state.rate - 0.01f }.coerceAtLeast(0) + 1) % rates.size]
                    onRate(next)
                },
                modifier = Modifier.semantics { contentDescription = "Reading speed ${formatRate(state.rate)}" },
            ) {
                Text(formatRate(state.rate), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
            }
        }
    }
}

private fun formatRate(rate: Float): String =
    (if (rate % 1f == 0f) "%.0f".format(rate) else "%s".format(rate.toString().trimEnd('0'))) + "×"

/** Small, translucent exit affordance shown in focus mode (back also exits). */
@Composable
private fun FocusExitButton(onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp), contentAlignment = Alignment.TopEnd) {
        FilledTonalButton(
            onClick = onExit,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            ),
        ) {
            Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Exit focus")
        }
    }
}

/** Hides the system bars while [enabled] (swipe from an edge to reveal them temporarily). */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(enabled) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (enabled) controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun OverflowMenu(
    source: DocumentSource,
    onShare: () -> Unit,
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
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = { open = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text("Copy Markdown") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = { open = false; onCopy() },
            )
            if (source is DocumentSource.Local) {
                DropdownMenuItem(
                    text = { Text("Open with…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                    onClick = { open = false; onOpenWith() },
                )
            }
            if (source is DocumentSource.Remote) {
                DropdownMenuItem(
                    text = { Text("Open in browser") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
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
