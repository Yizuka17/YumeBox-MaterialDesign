<div align="center">

**English** | [简体中文](README_ZH_HANS.md)

<img src="logo.webp" width="96" alt="YumeBox logo">

# YumeBox MD3

[![Latest release](https://img.shields.io/github/v/release/Yizuka17/YumeBox-MaterialDesign?logo=android)](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases/latest)
[![GitHub License](https://img.shields.io/github/license/Yizuka17/YumeBox-MaterialDesign?logo=gnu)](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/Yizuka17/YumeBox-MaterialDesign/total?logo=github)](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)

YumeBox MD3 is a Material Design 3 / Material You fork based on [YumeBox](https://github.com/YumeRiMoe/YumeBox), an open-source Android client powered by [mihomo](https://github.com/MetaCubeX/mihomo).

</div>

## Use

YumeBox MD3 currently only supports **Android 8.0 (API 26) and above systems**.

- Download the arm64-v8a release APK: [GitHub Releases](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)
- Project changelog: [YumeBox MD3](../website/en/update/history.mdx)
- Override configuration syntax: [override document](https://yumebox.oom-wg.dev/override)
- Daily Quote API and custom JSON format: [Daily Quote format guide](DailyQuote.md)

If this project is helpful to you, a Star is greatly appreciated.

## Core features

YumeBox MD3 keeps the core capabilities of the original project while continuing to improve the UI, configuration workflow, proxy runtime, and stability:

- **Material Design 3 / Material You**: the main UI is mostly migrated to MD3, with dynamic colors, dark-theme adaptation, navigation bar and Topbar blur effects, smoother motion, and more consistent components.
- **Override and configuration management**: supports multi-configuration switching, stacked applying, visual editing, preset routing templates, runtime configuration preview, and modifiers such as `start`, `end`, `merge`, and `force`.
- **Proxy runtime and node management**: supports Root Tun, Rule / Global / Direct modes, single-node and proxy-group delay tests, node sorting, node tags, icon/flag display, and persistent node selection.
- **Sub-Store, GeoX, and data management**: improves Sub-Store downloads, GeoX data updates, settings backup/restore, app data cleanup, and managed log export.
- **ACG home and Daily Quote**: supports wallpaper, sidebar controls, launch button, runtime information, quote display, manual/daily refresh, and mixed API/custom JSON sources.
- **Statistics, notifications, and stability**: provides connection pages, traffic statistics, service notifications, language and privacy settings, plus ongoing fixes for startup, dialogs, runtime state sync, package size, and background resource release.

- **In-app updates**: checks GitHub Releases directly, with prerelease support, download progress, cover previews and image saving.
- **Complete backups**: exports and restores profiles, overrides and bindings, wallpapers, statistics and other data; supports existing JSON settings backups.
- **Node cards and latency checks**: natural-height masonry cards with consistent spacing, plus concurrent latency checks while scrolling.
- **Bundled Sub-Store engine**: no separate engine extension required, with timed service shutdown.
- **Tablets and daily wallpapers**: a horizontal connection timer and labeled sidebar controls, with separate portrait/tablet APIs, custom image pools, daily rotation, cached fallbacks, and image preview/save actions.
- **HyperOS Super Island**: displays proxy status and live traffic; tapping the island resumes the previous page, while the proxy notification opens Home.

## Changelog

For the full changelog, APK assets, and version tags, see:

- [Project changelog](../website/en/update/history.mdx)
- [GitHub Releases](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)

## Roadmap

Future work will keep polishing the MD3 experience, configuration editing, runtime status display, traffic statistics, Sub-Store / GeoX flows, and overall stability. Remaining legacy UI boundaries will also be reduced gradually.

## Feedback and contribution

- Bugs and suggestions: [Issues](https://github.com/Yizuka17/YumeBox-MaterialDesign/issues)
- Contribution guide: [CONTRIBUTING](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/CONTRIBUTING.md)
- Translation contribution: fork this project and create or update the corresponding translation files under `locale/lang`.
- Discussion and feedback: [@OOM_WG](https://t.me/OOM_Group)

## Special

This project is developed based on [YumeBox](https://github.com/YumeRiMoe/YumeBox); **~~the author knows nothing about the code in this project, and the code is either available or unavailable, with no third case~~**; AI assistance is used during development for part of the code implementation, documentation, and troubleshooting, and thanks go to the original project and related open-source projects.

And the [third-party](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/docs/ThirdParty.md) libraries used in this project.
