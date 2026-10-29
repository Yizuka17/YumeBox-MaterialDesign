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



package com.github.yumelira.yumebox.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.yumelira.yumebox.data.controller.AcgWallpaperStorage
import com.github.yumelira.yumebox.data.controller.AppSettingsController
import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.AppLanguage
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.github.yumelira.yumebox.data.store.AppSettingsStore
import com.github.yumelira.yumebox.data.store.DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON
import com.github.yumelira.yumebox.data.store.FeatureStore
import com.github.yumelira.yumebox.data.store.Preference
import com.github.yumelira.yumebox.data.controller.UserSettingsBackupController
import com.github.yumelira.yumebox.presentation.theme.DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB
import com.github.yumelira.yumebox.presentation.theme.DEFAULT_CUSTOM_THEME_SEED_ARGB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppSettingsViewModel(
    private val settings: AppSettingsStore,
    private val featureStore: FeatureStore,
    private val controller: AppSettingsController,
    private val userSettingsBackupController: UserSettingsBackupController,
    private val acgWallpaperStorage: AcgWallpaperStorage,
) : ViewModel() {

    val initialSetupCompleted: Preference<Boolean> = settings.initialSetupCompleted
    val privacyPolicyAccepted: Preference<Boolean> = settings.privacyPolicyAccepted

    val themeMode: Preference<ThemeMode> = settings.themeMode
    val appLanguage: Preference<AppLanguage> = settings.appLanguage
    val colorTheme: Preference<AppColorTheme> = settings.colorTheme
    val themeSeedColorArgb: Preference<Long> = settings.themeAccentColorArgb
    val acgWallpaperSeedColorArgb: Preference<Long> = settings.acgWallpaperSeedColorArgb
    val invertOnPrimaryColors: Preference<Boolean> = settings.invertOnPrimaryColors
    val automaticRestart: Preference<Boolean> = settings.automaticRestart
    val autoUpdateCurrentProfileOnStart: Preference<Boolean> = settings.autoUpdateCurrentProfileOnStart
    val hideAppIcon: Preference<Boolean> = settings.hideAppIcon
    val excludeFromRecents: Preference<Boolean> = settings.excludeFromRecents
    val showTrafficNotification: Preference<Boolean> = settings.showTrafficNotification
    val bottomBarAutoHide: Preference<Boolean> = settings.bottomBarAutoHide
    val bottomBarUseLegacyStyle: Preference<Boolean> = settings.bottomBarUseLegacyStyle
    val acgMainUiEnabled: Preference<Boolean> = settings.acgMainUiEnabled
    val acgTabletModeEnabled = settings.acgTabletModeEnabled
    val acgDailyWallpaperEnabled = settings.acgDailyWallpaperEnabled
    val acgWallpaperUri: Preference<String> = settings.acgWallpaperUri
    val acgWallpaperZoom: Preference<Float> = settings.acgWallpaperZoom
    val acgWallpaperBiasX: Preference<Float> = settings.acgWallpaperBiasX
    val acgWallpaperBiasY: Preference<Float> = settings.acgWallpaperBiasY
    val acgHomeQuote: Preference<String> = settings.acgHomeQuote
    val acgHomeQuoteAuthor: Preference<String> = settings.acgHomeQuoteAuthor
    val acgDailyQuoteEnabled: Preference<Boolean> = settings.acgDailyQuoteEnabled
    val acgDailyQuote: Preference<String> = settings.acgDailyQuote
    val acgDailyQuoteAuthor: Preference<String> = settings.acgDailyQuoteAuthor
    val acgDailyQuoteDate: Preference<String> = settings.acgDailyQuoteDate
    val acgDailyQuoteApiUrl: Preference<String> = settings.acgDailyQuoteApiUrl
    private val acgDailyQuoteApiHistoryJson: Preference<String> = settings.acgDailyQuoteApiHistoryJson
    val acgCustomQuoteEnabled: Preference<Boolean> = settings.acgCustomQuoteEnabled
    val acgCustomQuoteListJson: Preference<String> = settings.acgCustomQuoteListJson
    val acgMergeCustomQuoteList: Preference<Boolean> = settings.acgMergeCustomQuoteList
    val acgSidebarExpanded: Preference<Boolean> = settings.acgSidebarExpanded
    val pageScale: Preference<Float> = settings.pageScale
    val singleNodeTest: Preference<Boolean> = settings.singleNodeTest
    val healthCheckConcurrency: Preference<Int> = settings.healthCheckConcurrency
    val screenshotProtectionEnabled: Preference<Boolean> = settings.screenshotProtectionEnabled
    val biometricUnlockEnabled: Preference<Boolean> = settings.biometricUnlockEnabled
    val exitUiWhenBackground: Preference<Boolean> = featureStore.exitUiWhenBackground

    val customUserAgent: Preference<String> = settings.customUserAgent

    private val _isRefreshingDailyAcgQuote = MutableStateFlow(false)
    val isRefreshingDailyAcgQuote: StateFlow<Boolean> = _isRefreshingDailyAcgQuote.asStateFlow()

    fun onThemeModeChange(mode: ThemeMode) = themeMode.set(mode)
    fun onAppLanguageChange(language: AppLanguage) = controller.applyAppLanguage(language)
    fun onColorThemeChange(theme: AppColorTheme) = colorTheme.set(theme)
    fun onThemeSeedColorChange(argb: Long) {
        themeSeedColorArgb.set(argb)
        colorTheme.set(AppColorTheme.Custom)
    }
    fun onInvertOnPrimaryColorsChange(enabled: Boolean) = invertOnPrimaryColors.set(enabled)
    fun resetThemeSeedColor() {
        themeSeedColorArgb.set(DEFAULT_CUSTOM_THEME_SEED_ARGB)
        colorTheme.set(AppColorTheme.Custom)
    }
    fun onBottomBarAutoHideChange(enabled: Boolean) = bottomBarAutoHide.set(enabled)
    fun onBottomBarUseLegacyStyleChange(enabled: Boolean) = bottomBarUseLegacyStyle.set(enabled)
    fun onAcgMainUiEnabledChange(enabled: Boolean) = acgMainUiEnabled.set(enabled)
    fun onAcgWallpaperUriChange(uri: String) = acgWallpaperUri.set(uri)
    fun applyAcgWallpaper(sourceUri: String): String {
        val localUri = acgWallpaperStorage.copyFromUri(sourceUri)
        acgWallpaperUri.set(localUri)
        return localUri
    }
    fun onAcgWallpaperSeedColorChange(argb: Long) = acgWallpaperSeedColorArgb.set(argb)
    fun onAcgWallpaperCropChange(zoom: Float, biasX: Float, biasY: Float) {
        acgWallpaperZoom.set(zoom.coerceIn(1f, 5f))
        acgWallpaperBiasX.set(biasX.coerceIn(-1f, 1f))
        acgWallpaperBiasY.set(biasY.coerceIn(-1f, 1f))
    }
    fun onAcgHomeQuoteChange(quote: String) = acgHomeQuote.set(quote)
    fun onAcgHomeQuoteAuthorChange(author: String) = acgHomeQuoteAuthor.set(author)
    fun onAcgDailyQuoteEnabledChange(enabled: Boolean) = acgDailyQuoteEnabled.set(enabled)
    fun onAcgDailyQuoteApiUrlChange(url: String) = acgDailyQuoteApiUrl.set(url.trim())
    fun onAcgCustomQuoteEnabledChange(enabled: Boolean) = acgCustomQuoteEnabled.set(enabled)
    fun onAcgCustomQuoteListJsonChange(json: String) = acgCustomQuoteListJson.set(json)
    fun onAcgMergeCustomQuoteListChange(enabled: Boolean) = acgMergeCustomQuoteList.set(enabled)
    fun refreshDailyAcgQuoteIfNeeded(force: Boolean = false) {
        if (_isRefreshingDailyAcgQuote.value) return
        if (!acgDailyQuoteEnabled.value && !acgCustomQuoteEnabled.value) {
            return
        }
        val today = todayKey()
        if (!force && acgDailyQuoteDate.value == today && acgDailyQuote.value.isNotBlank()) {
            return
        }

        _isRefreshingDailyAcgQuote.value = true
        viewModelScope.launch {
            val refreshStartedAt = System.currentTimeMillis()
            try {
                val currentQuote = DailyAcgQuote(
                    text = acgDailyQuote.value,
                    author = acgDailyQuoteAuthor.value,
                )
                val customQuoteListJson = acgCustomQuoteListJson.value
                val localQuotes = parseCustomQuoteList(customQuoteListJson)
                val resolvedLocalQuotes = if (isLegacyDefaultCustomQuoteList(customQuoteListJson)) {
                    val defaultQuoteListJson = DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON
                    acgCustomQuoteListJson.set(defaultQuoteListJson)
                    parseCustomQuoteList(defaultQuoteListJson)
                } else {
                    localQuotes
                }
                val apiHistoryQuotes = parseCustomQuoteList(acgDailyQuoteApiHistoryJson.value)
                val fallbackQuotes = mergeDistinctDailyAcgQuotes(
                    first = resolvedLocalQuotes,
                    second = apiHistoryQuotes,
                )
                val customQuotes = if (acgCustomQuoteEnabled.value) {
                    resolvedLocalQuotes
                } else {
                    emptyList()
                }
                val selection = selectDailyQuote(
                    apiEnabled = acgDailyQuoteEnabled.value,
                    customQuotes = customQuotes,
                    fallbackQuotes = fallbackQuotes,
                    apiUrl = acgDailyQuoteApiUrl.value,
                    excludedQuote = currentQuote.takeIf { force },
                ) ?: return@launch
                if (selection.fromApi) {
                    acgDailyQuoteApiHistoryJson.set(
                        buildApiQuoteHistoryJson(
                            currentHistory = apiHistoryQuotes,
                            quote = selection.quote,
                        )
                    )
                }
                val quote = selection.quote
                val elapsed = System.currentTimeMillis() - refreshStartedAt
                delay((MIN_ACG_QUOTE_REFRESH_LOADING_MS - elapsed).coerceAtLeast(0L))
                acgDailyQuote.set(quote.text)
                acgDailyQuoteAuthor.set(quote.author)
                acgDailyQuoteDate.set(today)
            } finally {
                _isRefreshingDailyAcgQuote.value = false
            }
        }
    }
    fun onAcgSidebarExpandedChange(expanded: Boolean) = acgSidebarExpanded.set(expanded)
    fun clearAcgWallpaperUri() {
        acgWallpaperStorage.clear()
        acgWallpaperUri.set("")
        acgWallpaperSeedColorArgb.set(DEFAULT_ACG_WALLPAPER_THEME_SEED_ARGB)
        onAcgWallpaperCropChange(zoom = 1f, biasX = 0f, biasY = 0f)
    }
    fun onPageScaleChange(scale: Float) = pageScale.set(scale)
    fun onAutomaticRestartChange(enabled: Boolean) = automaticRestart.set(enabled)
    fun onAutoUpdateCurrentProfileOnStartChange(enabled: Boolean) = autoUpdateCurrentProfileOnStart.set(enabled)
    fun onHideAppIconChange(hide: Boolean) = hideAppIcon.set(hide)
    fun onExcludeFromRecentsChange(exclude: Boolean) = excludeFromRecents.set(exclude)
    fun onShowTrafficNotificationChange(show: Boolean) = showTrafficNotification.set(show)
    fun onSingleNodeTestChange(enabled: Boolean) = singleNodeTest.set(enabled)
    fun onHealthCheckConcurrencyChange(concurrency: Int) = healthCheckConcurrency.set(
        when (concurrency) {
            16, 24, 32 -> concurrency
            else -> 8
        },
    )
    fun onScreenshotProtectionEnabledChange(enabled: Boolean) = screenshotProtectionEnabled.set(enabled)
    fun onBiometricUnlockEnabledChange(enabled: Boolean) = biometricUnlockEnabled.set(enabled)
    fun onExitUiWhenBackgroundChange(enabled: Boolean) = exitUiWhenBackground.set(enabled)

    fun applyCustomUserAgent(userAgent: String) = controller.applyCustomUserAgent(userAgent)

    fun exportUserSettingsBackup(): Result<String> = runCatching {
        runBlocking {
            userSettingsBackupController.exportToJson()
        }
    }

    fun importUserSettingsBackup(rawJson: String): Result<Unit> = runCatching {
        runBlocking {
            userSettingsBackupController.importFromJson(rawJson)
        }
        controller.applyAppLanguage(appLanguage.value)
        controller.applyCustomUserAgent(customUserAgent.value)
    }

    fun setInitialSetupCompleted(completed: Boolean) = initialSetupCompleted.set(completed)
    fun setPrivacyPolicyAccepted(accepted: Boolean) = privacyPolicyAccepted.set(accepted)
}

internal data class DailyAcgQuote(
    val text: String,
    val author: String,
)

private data class DailyAcgQuoteSelection(
    val quote: DailyAcgQuote,
    val fromApi: Boolean,
)

private val dailyQuoteClient = OkHttpClient.Builder()
    .connectTimeout(DAILY_QUOTE_API_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(DAILY_QUOTE_API_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(DAILY_QUOTE_API_ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .build()

internal const val DEFAULT_DAILY_QUOTE_API_URL = "https://v1.hitokoto.cn/?c=a&c=b&c=c"
private const val MIN_ACG_QUOTE_REFRESH_LOADING_MS = 550L
private const val CUSTOM_QUOTE_WEIGHT_WHEN_API_ENABLED = 0.25f
private const val API_HISTORY_QUOTE_LIMIT = 10
private const val DAILY_QUOTE_API_MAX_ATTEMPTS = 3
private const val DAILY_QUOTE_API_ATTEMPT_TIMEOUT_MS = 1_000L
private const val DAILY_QUOTE_API_RETRY_DELAY_MS = 80L

private const val LEGACY_DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON = """[
  {
    "text": "时间一分一秒流逝而去 终结一步一步迎面而来",
    "author": "恋文"
  }
]
"""

private const val LEGACY_DEFAULT_ACG_CUSTOM_QUOTE_LIST_WITH_UNSOURCED_JSON = """[
  {
    "text": "时间一分一秒流逝而去 终结一步一步迎面而来",
    "author": "恋文"
  },
  {
    "text": "在安静的线路上，等一朵云完成漫游",
    "author": "YumeBox"
  },
  {
    "text": "愿你历尽千帆，归来仍是少年。",
    "author": "自定义"
  },
  {
    "text": "所谓的成长，就是越来越能接受自己本来的样子。",
    "author": "某角色"
  }
]
"""

private fun isLegacyDefaultCustomQuoteList(rawJson: String): Boolean {
    val normalized = rawJson.trim()
    return normalized == LEGACY_DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON.trim() ||
        normalized == LEGACY_DEFAULT_ACG_CUSTOM_QUOTE_LIST_WITH_UNSOURCED_JSON.trim()
}

private fun mergeDistinctDailyAcgQuotes(
    first: List<DailyAcgQuote>,
    second: List<DailyAcgQuote>,
): List<DailyAcgQuote> {
    return (first + second).distinctBy { quote -> quote.quoteKey() }
}

private fun buildApiQuoteHistoryJson(
    currentHistory: List<DailyAcgQuote>,
    quote: DailyAcgQuote,
): String {
    val merged = mergeDistinctDailyAcgQuotes(listOf(quote), currentHistory)
        .let { quotes ->
            if (quotes.size <= API_HISTORY_QUOTE_LIMIT) {
                quotes
            } else {
                val mutableQuotes = quotes.toMutableList()
                while (mutableQuotes.size > API_HISTORY_QUOTE_LIMIT) {
                    mutableQuotes.removeAt(kotlin.random.Random.nextInt(mutableQuotes.size))
                }
                mutableQuotes
            }
        }
    return JSONArray().apply {
        merged.forEach { historyQuote ->
            put(
                JSONObject().apply {
                    put("text", historyQuote.text)
                    if (historyQuote.author.isNotBlank()) {
                        put("author", historyQuote.author)
                    }
                }
            )
        }
    }.toString()
}

private fun DailyAcgQuote.quoteKey(): Pair<String, String> {
    return text.trim() to author.trim()
}

private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private suspend fun selectDailyQuote(
    apiEnabled: Boolean,
    customQuotes: List<DailyAcgQuote>,
    fallbackQuotes: List<DailyAcgQuote>,
    apiUrl: String,
    excludedQuote: DailyAcgQuote? = null,
): DailyAcgQuoteSelection? {
    val customEnabled = customQuotes.isNotEmpty()
    val fallbackCandidates = fallbackQuotes.ifEmpty { customQuotes }
    val shouldAvoidExcluded = fallbackCandidates.hasDifferentQuote(excludedQuote)
    return when {
        apiEnabled && fallbackCandidates.isNotEmpty() -> {
            if (kotlin.random.Random.nextFloat() < CUSTOM_QUOTE_WEIGHT_WHEN_API_ENABLED) {
                fallbackCandidates.randomQuote(excludedQuote)
                    ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = false) }
                    ?: fetchDailyQuote(apiUrl, excludedQuote, shouldAvoidExcluded)
                        ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = true) }
            } else {
                fetchDailyQuote(apiUrl, excludedQuote, shouldAvoidExcluded)
                    ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = true) }
                    ?: fallbackCandidates.randomQuote(excludedQuote)
                        ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = false) }
            }
        }
        apiEnabled -> fetchDailyQuote(apiUrl, excludedQuote, shouldAvoidExcluded)
            ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = true) }
        customEnabled -> customQuotes.randomQuote(excludedQuote)
            ?.let { quote -> DailyAcgQuoteSelection(quote = quote, fromApi = false) }
        else -> null
    }
}

private fun List<DailyAcgQuote>.randomQuote(excludedQuote: DailyAcgQuote?): DailyAcgQuote? {
    if (isEmpty()) return null
    val candidates = filterNot { quote -> quote.isSameQuote(excludedQuote) }
    return candidates.randomOrNull() ?: randomOrNull()
}

private fun List<DailyAcgQuote>.hasDifferentQuote(excludedQuote: DailyAcgQuote?): Boolean {
    return any { quote -> !quote.isSameQuote(excludedQuote) }
}

private fun DailyAcgQuote.isSameQuote(other: DailyAcgQuote?): Boolean {
    if (other == null) return false
    return text.trim() == other.text.trim() && author.trim() == other.author.trim()
}

private suspend fun fetchDailyQuote(
    apiUrl: String,
    excludedQuote: DailyAcgQuote? = null,
    avoidExcluded: Boolean = false,
): DailyAcgQuote? {
    repeat(DAILY_QUOTE_API_MAX_ATTEMPTS) { attempt ->
        val quote = fetchDailyQuoteOnce(apiUrl)
        if (quote != null && (!avoidExcluded || !quote.isSameQuote(excludedQuote))) {
            return quote
        }
        if (attempt < DAILY_QUOTE_API_MAX_ATTEMPTS - 1) {
            delay(DAILY_QUOTE_API_RETRY_DELAY_MS)
        }
    }
    return null
}

private suspend fun fetchDailyQuoteOnce(apiUrl: String): DailyAcgQuote? = withContext(Dispatchers.IO) {
    runCatching {
        val url = apiUrl.trim().ifBlank { DEFAULT_DAILY_QUOTE_API_URL }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "YumeBox-MaterialDesign")
            .build()
        dailyQuoteClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            if (contentType.isNotBlank() && "json" !in contentType) return@withContext null
            parseDailyQuoteJson(response.body.string())
        }
    }.getOrNull()
}

private fun parseDailyQuoteJson(rawJson: String): DailyAcgQuote? {
    val json = JSONObject(rawJson)
    val text = listOf("hitokoto", "text", "quote", "content", "sentence")
        .firstNotNullOfOrNull { key -> json.optString(key).trim().takeIf(String::isNotBlank) }
        ?: return null
    val author = listOf("from_who", "author", "from", "source")
        .firstNotNullOfOrNull { key -> json.optString(key).trim().takeIf { it.isNotBlank() && it != "null" } }
        .orEmpty()
    return DailyAcgQuote(text = text, author = author)
}

internal fun parseCustomQuoteList(rawJson: String): List<DailyAcgQuote> = runCatching {
    val json = stripJsonLineComments(rawJson)
    if (json.isBlank()) return@runCatching emptyList()
    val array = JSONArray(json)
    buildList {
        for (index in 0 until array.length()) {
            when (val item = array.get(index)) {
                is String -> item.trim().takeIf(String::isNotBlank)?.let { add(DailyAcgQuote(it, "")) }
                is JSONObject -> parseDailyQuoteJson(item.toString())?.let(::add)
            }
        }
    }
}.getOrDefault(emptyList())

private fun stripJsonLineComments(raw: String): String {
    return raw.lineSequence()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString(separator = "\n")
}
