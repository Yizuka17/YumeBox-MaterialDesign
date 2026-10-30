package com.github.yumelira.yumebox.screen.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.rememberAsyncImagePainter
import com.github.panpf.sketch.request.ImageRequest
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.AppDialog
import com.github.yumelira.yumebox.presentation.component.AppTextFieldDialog
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.HapticSwitch
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.PreferenceValueItem
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.Title
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.screen.acg.AcgWallpaperManager
import com.github.yumelira.yumebox.screen.acg.WallpaperOptions
import com.github.yumelira.yumebox.screen.acg.WallpaperProfile
import com.github.yumelira.yumebox.update.ReleaseCoverPreview
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AcgWallpaperCropScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
@Destination<RootGraph>
fun AcgWallpaperConfigScreen(navigator: DestinationsNavigator) {
    val manager = koinInject<AcgWallpaperManager>()
    val settings = manager.settings
    val daily by settings.acgDailyWallpaperEnabled.state.collectAsState()
    val tablet by settings.acgTabletModeEnabled.state.collectAsState()
    val options by manager.options.collectAsState()
    val refreshing by manager.refreshing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var galleryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var galleryIsCache by rememberSaveable { mutableStateOf(false) }
    val gallery = WallpaperProfile.entries.firstOrNull { it.key == galleryKey }
    var manualDialog by remember { mutableStateOf(false) }
    var pickProfile by rememberSaveable { mutableStateOf(WallpaperProfile.Portrait.key) }
    val settingsListState = rememberLazyListState()
    val portraitGridState = rememberLazyGridState()
    val tabletGridState = rememberLazyGridState()
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }

    BackHandler(gallery != null) { galleryKey = null; preview = null }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val profile = WallpaperProfile.entries.first { it.key == pickProfile }
            val option = options.getValue(profile)
            navigator.navigate(AcgWallpaperCropScreenDestination(
                wallpaperUri = uri.toString(), profile = profile.key,
                initialZoom = option.zoom, initialBiasX = option.biasX, initialBiasY = option.biasY,
            ))
        }
    }
    val poolPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) {
            val profile = WallpaperProfile.entries.first { it.key == pickProfile }
            busy = true
            scope.launch {
                try { manager.addToPool(profile, uris.map { it.toString() }) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { context.toast(MLang.AppSettings.Wallpaper.SaveFailed) }
                finally { busy = false }
            }
        }
    }
    fun chooseSingle(profile: WallpaperProfile) {
        pickProfile = profile.key; manualDialog = false
        singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun refresh(profile: WallpaperProfile) {
        if (profile in manager.refreshing.value) return
        scope.launch {
            manager.refresh(profile, force = true)
        }
    }
    fun removePreview(path: String) {
        val profile = gallery ?: return
        scope.launch {
            if (galleryIsCache) manager.removeFromCache(profile, path) else manager.removeFromPool(profile, path)
            preview = null
        }
    }

    LaunchedEffect(daily, tablet) {
        if (daily) {
            launch { manager.refresh(WallpaperProfile.Portrait) }
            if (tablet) launch { manager.refresh(WallpaperProfile.Tablet) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopBar(
                title = if (gallery == null) MLang.AppSettings.Wallpaper.Title else if (galleryIsCache) "${profileTitle(gallery)} · ${MLang.AppSettings.Wallpaper.ApiCache}" else profileTitle(gallery),
                navigationIcon = {
                    IconButton(onClick = { if (gallery != null) galleryKey = null else navigator.popBackStack() }) {
                        Icon(AppMd3Icons.Navigation.Back, MLang.Component.Navigation.Back)
                    }
                },
            )
        },
    ) { innerPadding ->
        val insets = combinePaddingValues(innerPadding, rememberStandalonePageMainPadding())
        if (gallery != null) {
            val option = options.getValue(gallery)
            val pictures = if (galleryIsCache) option.history.filter { manager.fileUri(it) != null } else option.pool
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (gallery == WallpaperProfile.Tablet) 176.dp else 112.dp),
                state = if (gallery == WallpaperProfile.Tablet) tabletGridState else portraitGridState,
                modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (galleryIsCache && pictures.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(MLang.AppSettings.Wallpaper.CacheEmpty, Modifier.padding(24.dp)) }
                }
                items(pictures, key = { it }) { path ->
                    Box(Modifier.fillMaxWidth().aspectRatio(if (gallery == WallpaperProfile.Tablet) 16f / 9f else 3f / 4f)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = !busy) { preview = path }) {
                        WallpaperThumbnail(manager.fileUri(path), Modifier.fillMaxSize())
                        if (!galleryIsCache) IconButton(
                            onClick = { scope.launch { manager.removeFromPool(gallery, path) } },
                            modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(24.dp)),
                            enabled = !busy,
                        ) { Icon(AppMd3Icons.Action.Delete, MLang.AppSettings.Wallpaper.Remove) }
                    }
                }
                if (!galleryIsCache) item(key = "add") {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val minimumHeight = if (gallery == WallpaperProfile.Tablet) maxOf(104.dp, maxWidth * 9f / 16f) else maxWidth * 4f / 3f
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = minimumHeight)
                                .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable(enabled = !busy) {
                                    pickProfile = gallery.key
                                    poolPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                            else Icon(AppMd3Icons.Action.Add, null, Modifier.size(32.dp))
                            Text(if (busy) MLang.AppSettings.Wallpaper.Importing else MLang.AppSettings.Wallpaper.Add, Modifier.padding(8.dp))
                        }
                    }
                }
            }
        } else {
            ScreenLazyColumn(innerPadding = insets, lazyListState = settingsListState) {
                item {
                    Title(MLang.AppSettings.Experimental.WallpaperTitle)
                    Card {
                        PreferenceSwitchItem(
                            title = MLang.AppSettings.Wallpaper.Daily, summary = MLang.AppSettings.Wallpaper.DailySummary,
                            checked = daily, onCheckedChange = settings.acgDailyWallpaperEnabled::set,
                        )
                        PreferenceArrowItem(
                            title = MLang.AppSettings.Wallpaper.Manual, summary = MLang.AppSettings.Wallpaper.ManualSummary,
                            enabled = !daily,
                            onClick = { if (tablet) manualDialog = true else chooseSingle(WallpaperProfile.Portrait) },
                        )
                        PreferenceValueItem(title = MLang.AppSettings.Wallpaper.Reset,
                            onClick = { scope.launch { manager.resetDefaults(); context.toast(MLang.AppSettings.Experimental.ResetWallpaperSuccess) } })
                    }
                }
                WallpaperProfile.entries.forEach { profile ->
                    item {
                        val enabled = profile == WallpaperProfile.Portrait || tablet
                        Title(profileTitle(profile))
                        WallpaperProfileSection(
                            profile, options.getValue(profile), enabled, daily, profile in refreshing,
                            manager, onOpenPool = { galleryIsCache = false; galleryKey = profile.key },
                            onOpenCache = { galleryIsCache = true; galleryKey = profile.key }, onRefresh = { refresh(profile) },
                        )
                    }
                }
            }
        }
    }

    AppDialog(show = manualDialog, title = MLang.AppSettings.Wallpaper.Manual, onDismissRequest = { manualDialog = false }) {
        Column {
            PreferenceArrowItem(MLang.AppSettings.Wallpaper.ChoosePortrait, onClick = { chooseSingle(WallpaperProfile.Portrait) })
            PreferenceArrowItem(MLang.AppSettings.Wallpaper.ChooseTablet, enabled = tablet, onClick = { chooseSingle(WallpaperProfile.Tablet) })
        }
    }
    preview?.let { path ->
        manager.fileUri(path)?.let { uri ->
            ReleaseCoverPreview(uri, onDismiss = { preview = null }, localImage = true,
                description = MLang.AppSettings.Wallpaper.Preview, saveTitle = MLang.AppSettings.Wallpaper.SaveImage,
                onDelete = { removePreview(path) })
        }
    }
}

@Composable
private fun WallpaperProfileSection(
    profile: WallpaperProfile, option: WallpaperOptions, enabled: Boolean, daily: Boolean, refreshing: Boolean,
    manager: AcgWallpaperManager, onOpenPool: () -> Unit, onOpenCache: () -> Unit, onRefresh: () -> Unit,
) {
    var edit by remember { mutableStateOf(false) }
    var draft by remember(option.apiUrl) { mutableStateOf(option.apiUrl) }
    var invalid by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card {
        Column(Modifier.animateContentSize()) {
            PreferenceArrowItem(
                title = MLang.AppSettings.Wallpaper.Api,
                summary = if (enabled) option.apiUrl else MLang.AppSettings.Wallpaper.TabletDisabled,
                enabled = enabled, onClick = { draft = option.apiUrl; invalid = false; edit = true },
                endActions = { HapticSwitch(checked = option.apiEnabled, enabled = enabled, onCheckedChange = { manager.setApi(profile, enabled = it) }) },
            )
            PreferenceArrowItem(
                title = MLang.AppSettings.Wallpaper.Pool,
                summary = if (enabled) "${MLang.AppSettings.Wallpaper.PoolSummary} (${option.pool.size})" else MLang.AppSettings.Wallpaper.TabletDisabled,
                enabled = enabled, onClick = onOpenPool,
                endActions = { HapticSwitch(checked = option.poolEnabled, enabled = enabled, onCheckedChange = { manager.setPoolEnabled(profile, it) }) },
            )
            PreferenceArrowItem(
                title = MLang.AppSettings.Wallpaper.ApiCache,
                summary = "${option.history.count { manager.fileUri(it) != null }}",
                onClick = onOpenCache,
            )
            PreferenceValueItem(
                title = if (refreshing) MLang.AppSettings.Wallpaper.Refreshing else MLang.AppSettings.Wallpaper.Refresh,
                enabled = enabled && daily && !refreshing, onClick = onRefresh,
            )
        }
    }
    AppTextFieldDialog(
        show = edit, title = MLang.AppSettings.Wallpaper.ApiEdit, value = draft,
        onValueChange = { draft = it; invalid = false }, onDismissRequest = { edit = false }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        onConfirm = {
            if (AcgWallpaperManager.validApiUrl(draft)) { manager.setApi(profile, url = draft); edit = false }
            else invalid = true
        },
        supportingContent = { if (invalid) Text(MLang.AppSettings.Wallpaper.ApiInvalid, color = MaterialTheme.colorScheme.error) },
    )
}

@Composable
private fun WallpaperThumbnail(uri: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (uri == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer))
    else Image(rememberAsyncImagePainter(ImageRequest(LocalContext.current, uri)), MLang.AppSettings.Wallpaper.Preview, modifier, contentScale = contentScale)
}

private fun profileTitle(profile: WallpaperProfile) = when (profile) {
    WallpaperProfile.Portrait -> MLang.AppSettings.Wallpaper.Portrait
    WallpaperProfile.Tablet -> MLang.AppSettings.Wallpaper.Tablet
}
