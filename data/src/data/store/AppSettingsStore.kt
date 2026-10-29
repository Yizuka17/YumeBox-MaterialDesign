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



package com.github.yumelira.yumebox.data.store

import com.github.yumelira.yumebox.data.model.AppColorTheme
import com.github.yumelira.yumebox.data.model.AppLanguage
import com.github.yumelira.yumebox.data.model.MonetContrast
import com.github.yumelira.yumebox.data.model.MonetStyle
import com.github.yumelira.yumebox.data.model.ThemeMode
import com.tencent.mmkv.MMKV

class AppSettingsStore(externalMmkv: MMKV) : MMKVPreference(externalMmkv = externalMmkv) {

    val initialSetupCompleted by boolFlow(false)
    val privacyPolicyAccepted by boolFlow(false)

    val themeMode by enumFlow(ThemeMode.Auto)
    val appLanguage by enumFlow(AppLanguage.System)
    val colorTheme by enumFlow(AppColorTheme.MonetDynamic)
    val monetStyle by enumFlow(MonetStyle.TonalSpot)
    val monetContrast by enumFlow(MonetContrast.Standard)
    val monetColorIntensity by floatFlow(0.45f)
    val themeAccentColorArgb by longFlow(0xFFDA98ABL)
    val acgWallpaperSeedColorArgb by longFlow(0xFFDA98ABL)
    val invertOnPrimaryColors by boolFlow(false)
    val automaticRestart by boolFlow(false)
    val autoUpdateCurrentProfileOnStart by boolFlow(true)
    val hideAppIcon by boolFlow(false)
    val excludeFromRecents by boolFlow(false)
    val showTrafficNotification by boolFlow(true)
    val bottomBarAutoHide by boolFlow(true)
    val bottomBarUseLegacyStyle by boolFlow(false)
    val acgMainUiEnabled by boolFlow(false)
    val acgTabletModeEnabled by boolFlow(false)
    val acgDailyWallpaperEnabled by boolFlow(false)
    val acgWallpaperConfigJson by strFlow("{}")
    val acgWallpaperUri by strFlow("")
    val acgWallpaperZoom by floatFlow(1.0f)
    val acgWallpaperBiasX by floatFlow(0.0f)
    val acgWallpaperBiasY by floatFlow(0.0f)
    val acgHomeQuote by strFlow("时间一分一秒流逝而去 终结一步一步迎面而来")
    val acgHomeQuoteAuthor by strFlow("恋文")
    val acgDailyQuoteEnabled by boolFlow(false)
    val acgDailyQuote by strFlow("")
    val acgDailyQuoteAuthor by strFlow("")
    val acgDailyQuoteDate by strFlow("")
    val acgDailyQuoteApiUrl by strFlow("https://v1.hitokoto.cn/?c=a&c=b&c=c")
    val acgDailyQuoteApiHistoryJson by strFlow("[]")
    val acgCustomQuoteEnabled by boolFlow(false)
    val acgCustomQuoteListJson by strFlow(DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON)
    val acgMergeCustomQuoteList by boolFlow(false)
    val acgSidebarExpanded by boolFlow(true)
    val pageScale by floatFlow(1.0f)
    val singleNodeTest by boolFlow(true)
    val healthCheckConcurrency by intFlow(DEFAULT_HEALTH_CHECK_CONCURRENCY)
    val screenshotProtectionEnabled by boolFlow(false)
    val biometricUnlockEnabled by boolFlow(false)

    val customUserAgent by strFlow("")

    val homePreviewGuideShown by boolFlow(false)
    val superIslandEnabled by boolFlow(true)
    val topBarBlurEnabled by boolFlow(false)
    val classicHomeEnabled by boolFlow(false)
    val useSystemWallpaper by boolFlow(true)
    val systemWallpaperPermissionRequested by boolFlow(false)
    val moeWallpaperUri by strFlow("")
    val moeWallpaperSourceUri by strFlow("")
    val moeWallpaperZoom by floatFlow(1.0f)
    val moeWallpaperBiasX by floatFlow(0.0f)
    val moeWallpaperBiasY by floatFlow(0.0f)
    val moeHomeQuote by strFlow("さよなら君の声 忘れたりしない ずっと")
    val moeSidebarExpanded by boolFlow(true)
    val splitLeftRatio by floatFlow(0.42f)
    val predictiveBackEnabled by boolFlow(false)
    val predictiveBackMaxProgress by floatFlow(50.0f)
}

const val DEFAULT_HEALTH_CHECK_CONCURRENCY = 8
val SUPPORTED_HEALTH_CHECK_CONCURRENCY = listOf(8, 16, 24, 32)

const val DEFAULT_ACG_CUSTOM_QUOTE_LIST_JSON = """[
  {
    "text": "时间一分一秒流逝而去 终结一步一步迎面而来",
    "author": "恋文"
  },
  {
    "text": "在安静的线路上，等一朵云完成漫游",
    "author": "YumeBox"
  },
  {
    "text": "人类的赞歌是勇气的赞歌。",
    "author": "威尔·A·齐贝林"
  },
  {
    "text": "不相信自己的人，连努力的价值都没有。",
    "author": "迈特凯"
  }
]
"""
