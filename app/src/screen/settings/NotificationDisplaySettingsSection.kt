package com.github.yumelira.yumebox.screen.settings

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.model.NotificationDisplay
import com.github.yumelira.yumebox.data.model.NotificationField
import com.github.yumelira.yumebox.data.model.NotificationSlot
import com.github.yumelira.yumebox.data.model.NotificationValues
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceArrowItem
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.PreferenceSwitchItem
import com.github.yumelira.yumebox.presentation.component.Title
import dev.oom_wg.purejoy.mlang.MLang
import org.koin.compose.koinInject

@Composable
internal fun NotificationDisplaySettingsSection() {
    val settings = koinInject<AppSettingsStore>()
    val raw by settings.notificationDisplay.state.collectAsState()
    val showTraffic by settings.showTrafficNotification.state.collectAsState()
    val live by settings.liveUpdatesEnabled.state.collectAsState()
    val display = remember(raw) { NotificationDisplay.decode(raw) }
    val context = LocalContext.current
    val values = NotificationValues(
        MLang.AppSettings.Notice.PreviewProfile,
        com.github.yumelira.yumebox.core.locale.YumeTxt.Service.Notification.Running,
        if (showTraffic) MLang.AppSettings.Notice.PreviewNode else "",
        if (showTraffic) "1.2 MB/s" else "",
        "2027-01-01",
    )
    val first = display.first.resolve(values, values.profile)
    val second = display.second.resolve(values, if (showTraffic) "1.2 GB / 100 GB | 2027-01-01" else values.status)
    val right = display.right.resolve(values, "")
    fun save(update: (NotificationDisplay) -> NotificationDisplay) {
        settings.notificationDisplay.set(update(NotificationDisplay.decode(settings.notificationDisplay.value)).encode())
    }

    Title(MLang.AppSettings.Notice.Title)
    Card {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(MLang.AppSettings.Notice.Preview, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(first.ifBlank { "YumeBox MD3" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (right.isNotBlank()) Text(right, Modifier.weight(0.6f, fill = false), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                if (second.isNotBlank()) Text(second, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        NotificationSlotEditor(MLang.AppSettings.Notice.First, display.first) { slot -> save { it.copy(first = slot) } }
        NotificationSlotEditor(MLang.AppSettings.Notice.Second, display.second) { slot -> save { it.copy(second = slot) } }
        NotificationSlotEditor(MLang.AppSettings.Notice.Right, display.right) { slot -> save { it.copy(right = slot) } }
        if (Build.VERSION.SDK_INT >= 36) {
            PreferenceSwitchItem(
                title = MLang.AppSettings.Notice.Live,

                checked = live, onCheckedChange = settings.liveUpdatesEnabled::set,
            )
            fun canPromote(): Boolean = runCatching {
                context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
            }.getOrDefault(false)
            var promotionAllowed by remember(context) { mutableStateOf(canPromote()) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { promotionAllowed = canPromote() }
            if (live && !promotionAllowed) PreferenceArrowItem(
                title = MLang.AppSettings.Notice.SystemLiveSettings,
                onClick = {
                    val promoted = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS,
                        Uri.fromParts("package", context.packageName, null))
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    val general = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    if (listOf(promoted, general).none { intent -> runCatching { context.startActivity(intent) }.isSuccess }) {
                        context.toast(MLang.Util.Error.UnknownError)
                    }
                },
            )
        }
    }
}

@Composable
private fun NotificationSlotEditor(title: String, slot: NotificationSlot, onChange: (NotificationSlot) -> Unit) {
    val choices = NotificationField.entries
    val labels = listOf(
        MLang.AppSettings.Notice.Default, MLang.AppSettings.Notice.Profile, MLang.AppSettings.Notice.Status,
        MLang.AppSettings.Notice.Node, MLang.AppSettings.Notice.Traffic, MLang.AppSettings.Notice.Expiry,
        MLang.AppSettings.Notice.Custom, MLang.AppSettings.Notice.Hidden,
    )
    PreferenceEnumItem(title = title, currentValue = slot.field, items = labels, values = choices,
        onValueChange = { onChange(slot.copy(field = it)) })
    if (slot.field == NotificationField.Custom) {
        OutlinedTextField(
            value = slot.text,
            onValueChange = { onChange(slot.copy(text = it.take(64))) },
            label = { Text(title) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
