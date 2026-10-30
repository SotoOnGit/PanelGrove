package dev.soto.panelgrove.ui

import dev.soto.panelgrove.R
import android.content.pm.ActivityInfo
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.soto.panelgrove.MainActivity
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.engine.DocumentSession
import dev.soto.panelgrove.storage.ExportedPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReaderScreen(bookId: String, model: ReaderViewModel, library: LibraryViewModel, leave: () -> Unit) {
    val strings = rememberStrings()

    val session by model.session.collectAsStateWithLifecycle()
    val current by model.page.collectAsStateWithLifecycle()
    val offset by model.offset.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val error by model.message.collectAsStateWithLifecycle()
    val end by model.end.collectAsStateWithLifecycle()
    val books by library.books.collectAsStateWithLifecycle()
    val bookmarks by library.bookmarks.collectAsStateWithLifecycle()
    val preferences by library.preferences.collectAsStateWithLifecycle()
    val book = books.find { it.id == bookId } ?: session?.book
    val settings = book?.let { resolveSettings(preferences, it) } ?: preferences.reader
    var chrome by remember { mutableStateOf(true) }
    var options by rememberSaveable(bookId) { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var editionsMenu by rememberSaveable(bookId) { mutableStateOf(false) }
    var exportMenu by rememberSaveable(bookId) { mutableStateOf(false) }
    val preparingExport by model.preparingExport.collectAsStateWithLifecycle()
    val pendingExport by model.pendingExport.collectAsStateWithLifecycle()
    val timerPaused by model.statisticsPaused.collectAsStateWithLifecycle()
    val timerIdle by model.statisticsIdle.collectAsStateWithLifecycle()
    val editions = book?.let { languageEditions(it, books) }.orEmpty()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.exportResult(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    LaunchedEffect(pendingExport) {
        pendingExport?.let { payload ->
            if (!model.exportDispatched) {
                try {
                    val exported = PanelgroveJson.decodeFromString<ExportedPage>(payload)
                    val intent = model.app.pageExports.intent(exported)
                    model.exportDispatched()
                    exportLauncher.launch(intent)
                } catch (error: Exception) {
                    model.exportResult(null)
                    model.message.value = dev.soto.panelgrove.FailureMessages.user(error, strings[R.string.page_export_failed])
                }
            }
        }
    }
    val activity = LocalActivity.current as MainActivity
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val covered = options || contents || actions || editionsMenu || exportMenu || preparingExport || pendingExport != null || end
    val latestCovered by rememberUpdatedState(covered)
    val latestTimeout by rememberUpdatedState(preferences.statisticsIdleMinutes)
    SideEffect { model.readingVisibility(resumed && focused, covered, preferences.statisticsIdleMinutes) }
    val latestResumed by rememberUpdatedState(resumed)
    DisposableEffect(view) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            focused = hasFocus
            model.readingVisibility(latestResumed && hasFocus, latestCovered, latestTimeout)
            if (!hasFocus) model.save()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    LaunchedEffect(session) {
        if (session != null) {
            var ticks = 0
            while (true) {
                delay(1_000); model.statisticsTick()
                if (++ticks % 15 == 0) model.checkpointStatistics()
            }
        }
    }
    fun close() { model.close(); leave() }
    LaunchedEffect(bookId) { model.open(bookId) }
    LaunchedEffect(book?.uri, session?.book?.uri) {
        if (session != null && book != null && session!!.book.uri != book.uri) model.open(bookId, force = true)
    }
    DisposableEffect(bookId) { onDispose {
        if (activity.isChangingConfigurations) { model.readingVisibility(false, true, latestTimeout); model.save() }
        else model.close()
    } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            model.readingVisibility(resumed && view.hasWindowFocus(), latestCovered, latestTimeout)
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) model.save()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(settings, chrome) {
        val window = activity.window
        val oldBrightness = window.attributes.screenBrightness
        val oldOrientation = activity.requestedOrientation
        if (settings.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = settings.brightness }
        if (settings.rotationLock) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chrome) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        activity.readerKey = if (settings.volumeKeys) { next -> model.move(current + if (next) 1 else -1) } else null
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            if (settings.rotationLock) activity.requestedOrientation = oldOrientation
            activity.readerKey = null
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Keep hardware navigation current without resetting window flags on every page.
    SideEffect { activity.readerKey = if (settings.volumeKeys) { next -> model.move(model.page.value + if (next) 1 else -1) } else null }
    BackHandler { close() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(settings.background)).pointerInput(model) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) model.readingInteraction()
            }
        }
    }) {
        val spread = settings.spreads && (maxWidth > maxHeight || maxWidth >= 840.dp)
        val s = session
        if (s != null) {
            if (settings.mode == ReadingMode.PAGED) PagedReader(s, model, settings, spread, { chrome = !chrome })
            else VerticalReader(s, model, settings, { chrome = !chrome })
        }
        if (loading != null) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(); Text(loading!!, color = Color.White)
        }
        if (s == null && loading == null) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(error ?: strings[R.string.this_book_could_not_be_opened], color = Color.White)
            FilledTonalButton({ model.close(); model.open(bookId) }) { Text(strings[R.string.try_again]) }
            TextButton({ close() }) { Text(strings[R.string.back_to_library]) }
        }
        if (chrome) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, strings[R.string.back_to_library]) }
                        Text(book?.title ?: strings[R.string.app_name], Modifier.weight(1f), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        IconButton({ model.bookmark() }, enabled = s != null) {
                            val marked = s != null && bookmarks.any { it.bookId == bookId && it.pageId == s.index.pages[current].id }
                            Icon(if (marked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                if (marked) strings[R.string.remove_bookmark] else strings[R.string.bookmark_page], tint = if (marked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton({ options = true }) { Icon(Icons.Default.Tune, strings[R.string.reading_settings]) }
                        IconButton({ model.pauseStatistics() }, enabled = s != null) {
                            Icon(if (timerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                strings[if (timerPaused) R.string.statistics_resume_timer else R.string.statistics_pause_timer])
                        }
                        Box {
                            IconButton({ actions = true }) { Icon(Icons.Default.MoreVert, strings[R.string.reader_actions]) }
                            DropdownMenu(actions, { actions = false }) {
                                DropdownMenuItem(text = { Text(strings[R.string.language_editions]) }, enabled = editions.size > 1,
                                    onClick = { actions = false; editionsMenu = true })
                                DropdownMenuItem(text = { Text(strings[R.string.export_page]) }, enabled = s != null && !preparingExport && pendingExport == null,
                                    onClick = { actions = false; exportMenu = true })
                            }
                        }
                    }
                    if (s != null && error != null) Text(error!!, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    if (timerPaused || timerIdle) Text(strings[if (timerPaused) R.string.statistics_timer_paused else R.string.statistics_timer_idle],
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    if (preparingExport) {
                        Text(strings[R.string.preparing_page_export], Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (s != null) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val rtl = settings.direction == Direction.RTL
                        Text(strings[R.string.page_number, if (rtl) s.index.pages.size else 1], Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var sliding by remember(current) { mutableFloatStateOf(current.toFloat()) }
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
                            if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Slider(sliding, { sliding = it }, Modifier.weight(1f).semantics { contentDescription = strings[R.string.reading_page] },
                                onValueChangeFinished = { model.move(sliding.toInt()) },
                                valueRange = 0f..s.index.pages.lastIndex.coerceAtLeast(1).toFloat(), enabled = s.index.pages.size > 1,
                                thumb = { Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                                track = { state ->
                                    val fraction = (state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                                        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
                                    }
                                })
                        }
                        Text(strings[R.string.page_number, if (rtl) 1 else s.index.pages.size], Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ contents = true }, Modifier.weight(1f)) { Text(strings[R.string.contents]) }
                        IconButton({ model.move(current - 1) }, enabled = current > 0) { Icon(Icons.Default.SkipPrevious, strings[R.string.previous_page], Modifier.size(20.dp)) }
                        Text(strings[R.string.page_position, current + 1, s.index.pages.size], style = MaterialTheme.typography.bodyLarge)
                        IconButton({ model.move(current + 1) }) { Icon(Icons.Default.SkipNext, strings[R.string.next_page], Modifier.size(20.dp)) }
                        TextButton({ model.end.value = true }, Modifier.weight(1f).semantics { contentDescription = strings[R.string.finish_book] }) { Text(strings[R.string.finish]) }
                    }
                }
            }
        }
    }
    if (options && book != null) ReaderSettingsDialog(strings[R.string.this_book_s_settings], settings, { options = false },
        { updated -> library.change(book) { it.copy(settings = ReaderOverrides.from(updated)) } },
        { library.change(book) { it.copy(settings = ReaderOverrides()) } },
        preview = { draft -> session?.let { DisplayPagePreview(it, current, model, draft) } })
    if (contents && session != null) ContentsSheet(session!!, current, bookmarks.filter { it.bookId == bookId }, model, library) { contents = false }
    if (exportMenu && session != null) PageExportDialog(current, session!!.index.pages.size, { exportMenu = false }, model::prepareExport)
    if (editionsMenu && book != null) LanguageEditionsDialog(book, editions, library, { editionsMenu = false }, { edition ->
        if (edition.id != book.id) { model.close(); library.open(edition.id) }
    })
    if (end && book != null) {
        LaunchedEffect(book.id) { model.finish() }
        FinishBookScreen(book, nextUnread(book, books), library,
            close = { model.end.value = false },
            readNext = { next -> model.close(); library.open(next.id) },
            back = { close() },
            again = { model.readAgain(); model.end.value = false })
    }
}

@Composable private fun PagedReader(session: DocumentSession, model: ReaderViewModel, settings: ReaderSettings, spread: Boolean, toggle: () -> Unit) {
    val strings = rememberStrings()

    val current by model.page.collectAsStateWithLifecycle()
    val dimensions by model.dimensions.collectAsStateWithLifecycle()
    val groups = remember(session, spread, dimensions) { pageGroups(session.index.pages.map { page ->
        dimensions[page.id]?.let { page.copy(width = it.first, height = it.second) } ?: page
    }, spread) }
    val selectedGroup = groups.indexOfFirst { current in it }.coerceAtLeast(0)
    LaunchedEffect(session, groups, selectedGroup) { model.visiblePages(groups[selectedGroup]) }
    val pager = rememberPagerState(initialPage = selectedGroup, pageCount = { groups.size })
    var aligned by remember(session, groups) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(current, groups, aligned) {
        val index = groups.indexOfFirst { current in it }.coerceAtLeast(0)
        if (aligned && pager.settledPage != index && !pager.isScrollInProgress) pager.scrollToPage(index)
    }
    LaunchedEffect(pager, groups) {
        pager.scrollToPage(groups.indexOfFirst { model.page.value in it }.coerceAtLeast(0))
        aligned = true
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { group ->
            if (model.page.value !in groups[group]) model.move(groups[group].first())
        }
    }
    ReaderViewport(listOf(session, selectedGroup, settings.fit), tap = { x ->
        if (x in .3f.. .7f) toggle()
        else {
            val advance = if (settings.direction == Direction.RTL) x < .3f else x > .7f
            val target = pager.currentPage + if (advance) 1 else -1
            if (target >= groups.size) model.end.value = true
            else if (target >= 0) scope.launch { pager.animateScrollToPage(target) }
        }
    }) { zoomed ->
        HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = settings.direction == Direction.RTL, userScrollEnabled = !zoomed,
            key = { groups[it].first() }) { group ->
            val pages = if (settings.direction == Direction.RTL) groups[group].reversed() else groups[group]
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columnWidth = maxWidth / pages.size
                val contentHeight = if (settings.fit == FitMode.WIDTH) maxOf(maxHeight, pages.maxOf { index ->
                    val page = session.index.pages[index]
                    val measured = dimensions[page.id]
                    val width = measured?.first ?: page.width.takeIf { it > 0 } ?: 1125
                    val height = measured?.second ?: page.height.takeIf { it > 0 } ?: 1600
                    columnWidth * height.toFloat() / width
                }) else maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !zoomed)) {
                    Row(Modifier.fillMaxWidth().height(contentHeight)) { pages.forEach { index ->
                        PageContent(session, index, model, settings, Modifier.weight(1f).fillMaxHeight())
                    } }
                }
            }
        }
    }
    LaunchedEffect(current, session) {
        for (index in listOf(current + 1, current - 1)) if (index in session.index.pages.indices)
            try { model.app.engine.render(session, index, 1080, 1600) }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
    }
}

@Composable private fun VerticalReader(session: DocumentSession, model: ReaderViewModel, settings: ReaderSettings, toggle: () -> Unit) {
    val strings = rememberStrings()

    val current by model.page.collectAsStateWithLifecycle()
    val initial = remember(session) { model.page.value }
    val initialOffset = remember(session) { model.offset.value }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val density = LocalDensity.current
    var restored by remember(session) { mutableStateOf(false) }
    LaunchedEffect(list, session) {
        snapshotFlow {
            val layout = list.layoutInfo
            layout.visibleItemsInfo.filter { item ->
                val visible = minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)
                item.index in session.index.pages.indices && visible >= minOf(item.size, layout.viewportEndOffset - layout.viewportStartOffset) / 2
            }.map { it.index }
        }.distinctUntilChanged().collect(model::visiblePages)
    }
    LaunchedEffect(list, session) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull() }.collect { first ->
            if (first != null && !restored) {
                // Layout observation can resume during measure. Request the position for
                // the next measure instead of forcing a nested measure with scrollToItem.
                list.requestScrollToItem(initial, (initialOffset * first.size).toInt())
                restored = true
            }
        }
    }
    LaunchedEffect(list, restored) {
        if (restored) snapshotFlow {
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
            list.firstVisibleItemIndex to (list.firstVisibleItemScrollOffset.toFloat() / (first?.size ?: 1).coerceAtLeast(1))
        }.distinctUntilChanged().collect { (page, fraction) -> model.move(page.coerceAtMost(session.index.pages.lastIndex), fraction) }
    }
    LaunchedEffect(current) { if (restored && !list.isScrollInProgress && current != list.firstVisibleItemIndex) list.requestScrollToItem(current) }
    ReaderViewport(listOf(session, settings.mode), tap = { toggle() }) { zoomed ->
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), userScrollEnabled = !zoomed) {
            itemsIndexed(session.index.pages, key = { _, page -> page.id }) { index, page ->
                var ratio by remember(page.id) { mutableFloatStateOf(if (page.width > 0 && page.height > 0) page.width.toFloat() / page.height else .703125f) }
                PageContent(session, index, model, settings.copy(fit = FitMode.PAGE), Modifier.fillMaxWidth().height(width / ratio)) { w, h -> if (h > 0) ratio = w.toFloat() / h }
            }
            item { TextButton({ model.end.value = true }, Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp)) { Text(strings[R.string.end_of_book_finish_reading]) } }
        }
    }
}
}
