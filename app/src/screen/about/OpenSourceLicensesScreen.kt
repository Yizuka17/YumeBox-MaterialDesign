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



package com.github.yumelira.yumebox.screen.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.R
import com.github.yumelira.yumebox.presentation.component.AppActionBottomSheet
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.ScreenLazyColumn
import com.github.yumelira.yumebox.presentation.component.TopBar
import com.github.yumelira.yumebox.presentation.component.combinePaddingValues
import com.github.yumelira.yumebox.presentation.component.rememberStandalonePageMainPadding
import com.github.yumelira.yumebox.presentation.theme.AppTheme
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.oom_wg.purejoy.mlang.MLang

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Destination<RootGraph>
fun OpenSourceLicensesScreen(navigator: DestinationsNavigator) {
    val spacing = AppTheme.spacing

    var showLicenseSheet by remember { mutableStateOf(false) }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    BackHandler {
        navigator.popBackStack()
    }

    val libraries by produceLibraries {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            requireNotNull(com.github.yumelira.yumebox.App::class.java.classLoader?.getResourceAsStream("aboutlibraries.json"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
    val libraryItems = remember(libraries) { libraries?.libraries.orEmpty() }

    Scaffold(
        topBar = {
            TopBar(
                title = MLang.OpenSourceLicenses.Title,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val mainLikePadding = rememberStandalonePageMainPadding()
            ScreenLazyColumn(
                innerPadding = combinePaddingValues(innerPadding, mainLikePadding),
            ) {
                if (libraryItems.isNotEmpty()) {
                    items(
                        items = libraryItems,
                        key = { library -> "${library.uniqueId}:${library.artifactId}:${library.name}" },
                    ) { library ->
                        LibraryItem(
                            library = library,
                            onClick = {
                                selectedLibrary = library
                                showLicenseSheet = true
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(spacing.space24))
                    }
                }
            }

            selectedLibrary?.let { library ->
                LicenseBottomSheet(
                    show = showLicenseSheet,
                    library = library,
                    onDismiss = { showLicenseSheet = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryItem(
    library: Library,
    onClick: () -> Unit,
) {
    val spacing = AppTheme.spacing

    Card(
        modifier = Modifier.padding(bottom = spacing.space12),
        insideMargin = PaddingValues(spacing.space0),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(spacing.space16),
            verticalArrangement = Arrangement.spacedBy(spacing.space10),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                library.artifactVersion?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = spacing.space12),
                    )
                }
            }

            library.developers.firstOrNull()?.name?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (library.licenses.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.space6),
                    verticalArrangement = Arrangement.spacedBy(spacing.space6),
                ) {
                    library.licenses.forEach { license ->
                        LicenseChip(licenseName = license.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseChip(licenseName: String) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val opacity = AppTheme.opacity

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radii.radius12))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = opacity.subtle))
            .padding(horizontal = spacing.space10, vertical = spacing.space4),
    ) {
        Text(
            text = licenseName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LicenseBottomSheet(
    show: Boolean,
    library: Library,
    onDismiss: () -> Unit,
) {
    val spacing = AppTheme.spacing
    val componentSizes = AppTheme.sizes

    val scrollState = rememberScrollState()
    val licenseContent = remember(library) { library.strippedLicenseContent.takeIf { it.isNotEmpty() } }

    AppActionBottomSheet(
        show = show,
        title = library.name,
        onDismissRequest = onDismiss,
        content = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = componentSizes.dialogSheetMaxHeight)) {
                if (licenseContent != null) {
                    Text(
                        modifier = Modifier.verticalScroll(scrollState),
                        text = licenseContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(spacing.space16),
                    ) {
                        Text(
                            text = MLang.OpenSourceLicenses.LicenseSheet.NoContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    )
}
