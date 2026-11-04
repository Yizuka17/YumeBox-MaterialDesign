package com.github.yumelira.yumebox.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.yumelira.yumebox.common.util.toast
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.presentation.component.Card
import com.github.yumelira.yumebox.presentation.component.PreferenceEnumItem
import com.github.yumelira.yumebox.presentation.component.PreferenceValueItem
import com.github.yumelira.yumebox.presentation.component.Title
import dev.oom_wg.purejoy.mlang.MLang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
internal fun AppTypographySettingsSection() {
    val settings = koinInject<AppSettingsStore>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customFont by settings.customFont.state.collectAsState()
    val weight by settings.appFontWeight.state.collectAsState()
    val size by settings.appFontSizeScale.state.collectAsState()
    var draftSize by remember(size) { mutableFloatStateOf(size) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val previous = settings.customFont.value
                        settings.customFont.set(AppFontStorage.import(context, uri))
                        AppFontStorage.remove(context, previous)
                    }
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) {
                    android.util.Log.w("AppFont", "Font import failed", error)
                    context.toast(MLang.AppSettings.Typography.ImportFailed)
                }
                finally { importing = false }
            }
        }
    }
    Title(MLang.AppSettings.Typography.Title)
    Card {
        PreferenceValueItem(
            title = MLang.AppSettings.Typography.Font,
            summary = AppFontStorage.displayName(customFont).ifBlank { MLang.AppSettings.Typography.System },
            onClick = { if (!importing) picker.launch(arrayOf("*/*")) },
        )
        val weights = listOf(0, 100, 200, 300, 400, 500, 600, 700, 800, 900)
        PreferenceEnumItem(
            title = MLang.AppSettings.Typography.Weight,
            currentValue = weight,
            items = weights.map { if (it == 0) MLang.AppSettings.Typography.System else it.toString() },
            values = weights,
            onValueChange = settings.appFontWeight::set,
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(MLang.AppSettings.Typography.Size + " (" + (draftSize * 100).roundToInt() + "%)",
                style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = draftSize,
                onValueChange = { draftSize = it },
                onValueChangeFinished = { settings.appFontSizeScale.set(draftSize) },
                valueRange = 0.85f..1.35f,
                steps = 9,
            )
        }
        PreferenceValueItem(
            title = MLang.AppSettings.Typography.Reset,
            onClick = {
                if (!importing) {
                    val previous = settings.customFont.value
                    settings.customFont.set("")
                    settings.appFontWeight.set(0)
                    settings.appFontSizeScale.set(1f)
                    scope.launch(Dispatchers.IO) { AppFontStorage.remove(context, previous) }
                }
            },
        )
    }
}
