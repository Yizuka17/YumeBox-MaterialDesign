/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeLira 2025 - Present
 *
 */


package com.github.yumelira.yumebox.screen.profiles
import com.github.yumelira.yumebox.presentation.theme.UiDp
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetCloseAction
import com.github.yumelira.yumebox.presentation.component.AppBottomSheetConfirmAction
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.Md3ELoading
import com.github.yumelira.yumebox.presentation.component.Md3EIndeterminateCircularWavyProgressIndicator
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3DropdownPreference
import com.github.yumelira.yumebox.presentation.component.md3.YumeMd3OutlinedTextField
import com.github.yumelira.yumebox.presentation.icon.AppMd3Icons
import com.github.yumelira.yumebox.runtime.api.Profile
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.math.max

@Composable
internal fun AddProfileSheet(
    show: MutableState<Boolean>,
    profileToEdit: Profile? = null,
    importUrl: String? = null,
    onAddProfile: (name: String, source: String, type: Profile.Type, interval: Long, fileUri: android.net.Uri?) -> Unit,
    onUpdateProfile: (uuid: UUID, name: String, source: String, interval: Long) -> Unit,
    onDownloadComplete: () -> Unit,
    profilesViewModel: ProfilesViewModel
) {
    val configuration = LocalConfiguration.current
    val downloadSheetContentHeight = configuration.screenHeightDp.dp * 0.3f
    val downloadCompleteSheetContentHeight = configuration.screenHeightDp.dp * 0.42f
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    val downloadProgress by profilesViewModel.downloadProgress.collectAsState()
    val uiState by profilesViewModel.uiState.collectAsState()
    var hasShownCompleteAnimation by remember { mutableStateOf(false) }
    var stableSheetHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(show.value) {
        if (!show.value) {
            hasShownCompleteAnimation = false
            isDownloading = false
        }
    }

    val urlPattern = remember {
        Regex(
            pattern = "^https?://\\S+$", options = setOf(RegexOption.IGNORE_CASE)
        )
    }

    val readClipboardAndCheckUrl: () -> String? = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val clipText = item?.text?.toString()?.trim() ?: ""
                if (clipText.isNotEmpty() && urlPattern.matches(clipText)) {
                    clipText
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    val clearAllState = {
        name = ""
        url = ""
        filePath = ""
        fileName = ""
        error = ""
        isDownloading = false
        hasShownCompleteAnimation = false
    }


    val clearCurrentTypeState = {
        when (selectedTypeIndex) {
            0 -> url = ""
            1 -> {
                filePath = ""
                fileName = ""
            }

            2 -> {
            }
        }
        error = ""
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            context.toast(MLang.ProfilesPage.QrScanner.NeedCamera, Toast.LENGTH_LONG)
            selectedTypeIndex = 0
        }
    }

    LaunchedEffect(selectedTypeIndex) {
        if (selectedTypeIndex == 2 && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showCameraPreview by remember(show.value, selectedTypeIndex, isDownloading, hasCameraPermission) {
        derivedStateOf {
            show.value && selectedTypeIndex == 2 && !isDownloading && hasCameraPermission
        }
    }


    DisposableEffect(show.value, profileToEdit, importUrl) {
        if (show.value) {
            clearAllState()
            if (profileToEdit != null) {
                name = profileToEdit.name
                if (profileToEdit.type == Profile.Type.Url) {
                    selectedTypeIndex = 0
                    url = profileToEdit.source
                } else {
                    selectedTypeIndex = 1
                    filePath = profileToEdit.source
                    fileName = if (profileToEdit.source.isNotEmpty()) File(profileToEdit.source).name else ""
                }
            } else if (!importUrl.isNullOrBlank()) {
                selectedTypeIndex = 0
                url = importUrl
            } else {
                selectedTypeIndex = 0
                try {
                    val clipboardUrl = readClipboardAndCheckUrl()
                    if (clipboardUrl != null) {
                        url = clipboardUrl
                    }
                } catch (_: Exception) {
                }
            }
        }
        onDispose { }
    }
    LaunchedEffect(uiState.error) {
        val errorMessage = uiState.error
        if (errorMessage != null) {
            context.toast(errorMessage, Toast.LENGTH_LONG)
            if (isDownloading) {
                isDownloading = false
                error = errorMessage
            }
            profilesViewModel.clearError()
        }
    }

    LaunchedEffect(downloadProgress?.isCompleted, isDownloading) {
        if (isDownloading && downloadProgress?.isCompleted == true && !hasShownCompleteAnimation) {
            hasShownCompleteAnimation = true
            onDownloadComplete()
        }
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null && isDownloading && !hasShownCompleteAnimation) {
            hasShownCompleteAnimation = true
            onDownloadComplete()
        }
        if (uiState.message != null) {
            profilesViewModel.clearMessage()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val actualFileName = context.contentResolver.query(
                it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: MLang.ProfilesPage.Message.UnknownFile

            val extension = actualFileName.substringAfterLast(".", "")
            if (!extension.equals("yaml", ignoreCase = true) && !extension.equals(
                    "yml", ignoreCase = true
                )
            ) {
                error = MLang.ProfilesPage.Validation.YamlOnly
                return@let
            }

            filePath = it.toString()
            error = ""
            fileName = actualFileName

            val fileNameWithoutExt = actualFileName.substringBeforeLast(".")
            if (name.isBlank() || name == actualFileName) {
                name = fileNameWithoutExt.ifBlank { MLang.ProfilesPage.Input.NewProfile }
            }
        }
    }

    val qrImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val result = readQrFromImage(context, it)
                        if (result != null) {
                            url = result
                            selectedTypeIndex = 0
                            context.toast(MLang.ProfilesPage.QrScanner.RecognizeSuccess)
                        } else {
                            context.toast(MLang.ProfilesPage.QrScanner.RecognizeFailed)
                        }
                    } catch (e: Exception) {
                        context.toast(MLang.ProfilesPage.QrScanner.RecognizeError.format(e.message ?: ""))
                    }
                }
            }
        }

    val dismissSheet = {
        if (!isDownloading) {
            show.value = false
            profilesViewModel.clearDownloadProgress()
        }
    }

    BackHandler(enabled = show.value && isDownloading) {
        // Keep the sheet state consistent while the import job is owned by the ViewModel.
    }

    fun submitProfile() {
        if (selectedTypeIndex == 2 || isDownloading) {
            return
        }
        if (selectedTypeIndex == 0 && url.isBlank()) {
            error = MLang.ProfilesPage.Validation.EnterUrl
            return
        }
        if (selectedTypeIndex == 1 && filePath.isBlank()) {
            error = MLang.ProfilesPage.Validation.SelectFile
            return
        }

        keyboardController?.hide()
        profilesViewModel.clearError()
        hasShownCompleteAnimation = false
        isDownloading = true

        if (selectedTypeIndex == 0) {
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    name,
                    url,
                    profileToEdit.interval
                )
            } else {
                onAddProfile(
                    name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
                    url,
                    Profile.Type.Url,
                    0L,
                    null
                )
            }
        } else {
            if (profileToEdit != null) {
                onUpdateProfile(
                    profileToEdit.uuid,
                    name,
                    profileToEdit.source,
                    profileToEdit.interval
                )
            } else {
                onAddProfile(
                    name.ifBlank { MLang.ProfilesPage.Input.NewProfile },
                    filePath,
                    Profile.Type.File,
                    0L,
                    filePath.toUri()
                )
            }
        }
    }

    AppActionBottomSheet(
        show = show.value,
        title = if (profileToEdit != null) MLang.ProfilesPage.Sheet.EditTitle else MLang.ProfilesPage.Sheet.AddTitle,
        startAction = {
            if (!isDownloading) {
                AppBottomSheetCloseAction(
                    contentDescription = "Cancel",
                    onClick = dismissSheet,
                )
            }
        },
        endAction = {
            if (!isDownloading && selectedTypeIndex != 2) {
                AppBottomSheetConfirmAction(
                    contentDescription = "Confirm",
                    onClick = { submitProfile() },
                )
            }
        },
        onDismissRequest = dismissSheet,
        allowDismiss = !isDownloading,
    ) {
        val stableSheetHeight = remember(stableSheetHeightPx, density) {
            if (stableSheetHeightPx <= 0) UiDp.dp0 else with(density) { stableSheetHeightPx.toDp() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                .padding(bottom = UiDp.dp16),
        ) {
            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { it }) + fadeIn()) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(220),
                                    targetOffsetX = { -it / 3 }) + fadeOut())
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(220),
                            initialOffsetX = { -it / 3 }) + fadeIn()) togetherWith
                                (slideOutHorizontally(animationSpec = tween(260), targetOffsetX = { it }) + fadeOut())
                    }
                },
                label = "ProfileImportContentSwitch",
            ) { downloading ->
                if (downloading) {
                    DownloadProgressContent(
                        downloadProgress = downloadProgress,
                        stableSheetHeightPx = stableSheetHeightPx,
                        stableSheetHeight = stableSheetHeight,
                        downloadSheetContentHeight = downloadSheetContentHeight,
                        downloadCompleteSheetContentHeight = downloadCompleteSheetContentHeight,
                    )
                } else {
                    ProfileFormContent(
                        selectedTypeIndex = selectedTypeIndex,
                        profileLocked = profileToEdit != null,
                        name = name,
                        url = url,
                        fileName = fileName,
                        error = error,
                        hasCameraPermission = hasCameraPermission,
                        showCameraPreview = showCameraPreview,
                        onContainerMeasured = { stableSheetHeightPx = max(stableSheetHeightPx, it.height) },
                        onTypeSelected = {
                            selectedTypeIndex = it
                            clearCurrentTypeState()
                        },
                        onNameChange = {
                            name = it
                            error = ""
                        },
                        onUrlChange = {
                            url = it
                            error = ""
                        },
                        onPickFile = { launcher.launch("*/*") },
                        onSelectQrImage = { qrImageLauncher.launch("image/*") },
                        onQrScanned = { scannedUrl ->
                            url = scannedUrl
                            selectedTypeIndex = 0
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressContent(
    downloadProgress: DownloadProgress?,
    stableSheetHeightPx: Int,
    stableSheetHeight: androidx.compose.ui.unit.Dp,
    downloadSheetContentHeight: androidx.compose.ui.unit.Dp,
    downloadCompleteSheetContentHeight: androidx.compose.ui.unit.Dp,
) {
    val isCompleted = downloadProgress?.isCompleted == true
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                if (isCompleted) {
                    downloadCompleteSheetContentHeight
                } else if (stableSheetHeightPx > 0) {
                    stableSheetHeight
                } else {
                    downloadSheetContentHeight
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16, Alignment.CenterVertically),
    ) {
        AnimatedContent(
            targetState = isCompleted,
            modifier = Modifier.size(UiDp.dp48),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "ProgressIcon",
        ) { complete ->
            if (complete) {
                Icon(
                    imageVector = AppMd3Icons.Shell.PackageComplete,
                    contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(UiDp.dp16))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(UiDp.dp10),
                )
            } else {
                Md3EIndeterminateCircularWavyProgressIndicator(
                    modifier = Modifier.size(UiDp.dp48),
                )
            }
        }

        downloadProgress?.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileFormContent(
    selectedTypeIndex: Int,
    profileLocked: Boolean,
    name: String,
    url: String,
    fileName: String,
    error: String,
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    onContainerMeasured: (androidx.compose.ui.unit.IntSize) -> Unit,
    onTypeSelected: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onSelectQrImage: () -> Unit,
    onQrScanned: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged(onContainerMeasured),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        ProfileTypeSelectorCard(
            selectedTypeIndex = selectedTypeIndex,
            profileLocked = profileLocked,
            onTypeSelected = onTypeSelected,
        )

        Crossfade(
            targetState = selectedTypeIndex,
            animationSpec = tween(200),
            label = "ProfileTypeContent",
        ) { typeIndex ->
            when (typeIndex) {
                2 -> QrScannerContent(
                    hasCameraPermission = hasCameraPermission,
                    showCameraPreview = showCameraPreview,
                    onSelectQrImage = onSelectQrImage,
                    onQrScanned = onQrScanned,
                )

                else -> ManualProfileContent(
                    typeIndex = typeIndex,
                    profileLocked = profileLocked,
                    name = name,
                    url = url,
                    fileName = fileName,
                    error = error,
                    onNameChange = onNameChange,
                    onUrlChange = onUrlChange,
                    onPickFile = onPickFile,
                )
            }
        }
    }
}

@Composable
private fun ProfileTypeSelectorCard(
    selectedTypeIndex: Int,
    profileLocked: Boolean,
    onTypeSelected: (Int) -> Unit,
) {
    Card {
        Box(
            modifier = Modifier.alpha(if (profileLocked) 0.5f else 1f),
        ) {
            YumeMd3DropdownPreference(
                title = MLang.ProfilesPage.Type.Title,
                items = listOf(
                    MLang.ProfilesPage.Type.Subscription,
                    MLang.ProfilesPage.Type.LocalFile,
                    MLang.ProfilesPage.Type.QrScan,
                ),
                selectedIndex = selectedTypeIndex,
                onSelectedIndexChange = onTypeSelected,
                enabled = !profileLocked,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun QrScannerContent(
    hasCameraPermission: Boolean,
    showCameraPreview: Boolean,
    onSelectQrImage: () -> Unit,
    onQrScanned: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiDp.dp200)
                .clip(RoundedCornerShape(UiDp.dp12))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showCameraPreview) {
                key("qr_scanner_stable") {
                    StableQrScanner(onScanned = onQrScanned)
                }
            } else if (!hasCameraPermission) {
                Text(text = MLang.ProfilesPage.QrScanner.NeedPermission)
            } else {
                Md3ELoading(modifier = Modifier.size(UiDp.dp48))
            }
        }

        TextButton(
            onClick = onSelectQrImage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = MLang.ProfilesPage.QrScanner.SelectFromAlbum)
        }
    }
}

@Composable
private fun ManualProfileContent(
    typeIndex: Int,
    profileLocked: Boolean,
    name: String,
    url: String,
    fileName: String,
    error: String,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onPickFile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        YumeMd3OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = MLang.ProfilesPage.Input.ProfileName,
            modifier = Modifier.fillMaxWidth(),
        )

        if (typeIndex == 0) {
            YumeMd3OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = MLang.ProfilesPage.Input.SubscriptionUrl,
                maxLines = 2,
                readOnly = profileLocked,
                enabled = !profileLocked,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            YumeMd3OutlinedTextField(
                value = fileName,
                onValueChange = {},
                label = MLang.ProfilesPage.Input.SelectFile,
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onPickFile,
                    ),
            )
        }

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
