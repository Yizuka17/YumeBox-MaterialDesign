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



package com.github.yumelira.yumebox.di

import com.github.yumelira.yumebox.data.gateway.LogRecordGateway
import com.github.yumelira.yumebox.screen.home.HomeViewModel
import com.github.yumelira.yumebox.screen.log.LogViewModel
import com.github.yumelira.yumebox.screen.profiles.ProfilesViewModel
import com.github.yumelira.yumebox.screen.settings.AccessControlViewModel
import com.github.yumelira.yumebox.screen.settings.AppDataManagementViewModel
import com.github.yumelira.yumebox.screen.settings.AppSettingsViewModel
import com.github.yumelira.yumebox.screen.settings.NetworkSettingsViewModel
import com.github.yumelira.yumebox.service.LogRecordServiceGateway
import com.github.yumelira.yumebox.update.GitHubUpdateManager
import com.github.yumelira.yumebox.update.GitHubUpdateViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appIntegrationModule = module {
    single { com.github.yumelira.yumebox.screen.acg.AcgWallpaperManager(androidContext(), get()) }
    single { com.github.yumelira.yumebox.backup.GlobalBackupController(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    single<LogRecordGateway> { LogRecordServiceGateway() }
    single { GitHubUpdateManager(androidContext()) }
}

val appViewModelModule = module {
    viewModel { AppSettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { ProfilesViewModel(androidApplication(), get(), get()) }
    viewModel { NetworkSettingsViewModel(androidApplication(), get(), get(), get()) }
    viewModel { AccessControlViewModel(androidApplication(), get(), get()) }
    viewModel { AppDataManagementViewModel(get(), get()) }
    viewModel { LogViewModel(get()) }
    viewModel { GitHubUpdateViewModel(get()) }
}

val appModule: List<Module> = coreDiModules + listOf(
    appIntegrationModule,
    appViewModelModule,
) + featureSubStoreModules + featureProxyModules + featureOverrideModules + featureMetaModules
