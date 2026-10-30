package com.github.yumelira.yumebox.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.github.yumelira.yumebox.presentation.theme.AppMotion

val LocalTabletModeEnabled = compositionLocalOf { false }

@Composable
fun rememberTabletLayout(): Boolean {
    val config = LocalConfiguration.current
    val scale = LocalDensity.current.density / config.densityDpi.let { it / 160f }
    val width = config.screenWidthDp / scale
    val height = config.screenHeightDp / scale
    return LocalTabletModeEnabled.current && width >= 480 && (config.screenWidthDp >= 600 || width / height >= .85f)
}

@Composable
fun TabletPageSidebar(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.width(if (LocalConfiguration.current.screenWidthDp < 840) 168.dp else 200.dp)
            .fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Text(title, Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun TabletSidebarAction(title: String, icon: ImageVector? = null, selected: Boolean = false,
    enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit) {
    val isSelected = selected
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = AppMotion.color(), label = "sidebar_label")
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = AppMotion.color(), label = "sidebar_selection")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(background).semantics(mergeDescendants = true) { this.selected = isSelected }
        .clickable(enabled = enabled, role = role, onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = color.copy(alpha = if (enabled) 1f else .38f))
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color.copy(alpha = if (enabled) 1f else .38f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
