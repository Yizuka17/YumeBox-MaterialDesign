<div align="center">

**简体中文** | [English](README.md)

<img src="logo.webp" style="width: 96px;" alt="logo">

## YumeBox MD3

[![Latest release](https://img.shields.io/github/v/release/Yizuka17/YumeBox-MaterialDesign?label=Release&logo=github)](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases/latest)
[![GitHub License](https://img.shields.io/github/license/Yizuka17/YumeBox-MaterialDesign?logo=gnu)](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/Yizuka17/YumeBox-MaterialDesign/total?logo=github)](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)

**YumeBox MD3 是基于 [YumeBox](https://github.com/YumeRiMoe/YumeBox) 的 Material Design 3 / Material You 分支，一个基于 [mihomo](https://github.com/MetaCubeX/mihomo) 内核的开源 Android 客户端。**

</div>

## 使用

YumeBox MD3 目前仅支持 **Android 8.0（API 26）及以上系统**。

- 下载 arm64-v8a release 安装包：[GitHub Releases](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)
- 项目更新日志：[YumeBox MD3](../website/update/history.mdx)
- 覆写配置语法：[override 文档](https://yumebox.oom-wg.dev/override)
- 每日一言 API 与用户自定义 JSON 格式：[每日一言格式说明](DailyQuote_ZH_HANS.md)

如果这个项目对你有帮助，欢迎点下 Star，这是持续更新的动力。

## 核心特性

YumeBox MD3 在保留原项目核心能力的基础上，主要围绕界面体验、配置管理、代理运行和稳定性继续扩展：

- **Material Design 3 / Material You**：主体界面已基本完成 MD3 化，支持动态取色、深色主题、导航栏与 Topbar 模糊、页面动效和更统一的组件风格。
- **覆写与配置管理**：支持多配置切换、叠加应用、可视化编辑、预设分流模板、运行时配置预览，以及 `start`、`end`、`merge`、`force` 等覆写修饰符。
- **代理运行与节点管理**：支持 Root Tun、规则 / 全局 / 直连模式、单节点与代理组测速、节点排序、节点标签与图标/旗帜展示，以及节点选择持久化。
- **Sub-Store、GeoX 与数据管理**：优化 Sub-Store 下载流程、GeoX 数据更新、用户设置备份/恢复、应用数据清理和托管日志导出。
- **ACG 首页与一言**：支持壁纸、侧栏、启动按钮、运行信息、一言展示、手动/每日刷新，以及 API 与用户自定义 JSON 混合来源。
- **统计、通知与稳定性**：提供连接页面、流量统计、服务通知、语言与隐私设置，并持续优化启动、弹窗、运行状态同步、打包体积和后台资源释放。

- **应用内更新**：直接检测 GitHub Releases，支持预发布版本、下载进度和封面预览保存。
- **完整备份**：导出和恢复配置、覆写及绑定、壁纸、统计等数据，兼容旧版 JSON 设置备份。
- **节点卡片与测速**：自适应高度的瀑布流卡片、统一间距，并发检测延迟时可滑动列表。
- **内置 Sub-Store 引擎**：无需另装引擎扩展包，支持定时关闭服务。
- **平板与每日壁纸**：平板侧栏显示横排时长和功能名称；竖屏、平板可分别设置壁纸 API 与自定义图片池，支持每日更换和缓存兜底。
- **HyperOS 超级岛**：显示代理状态与实时流量，点击超级岛回到退出前的页面，代理通知仍可直达首页。

## 更新日志

完整更新日志、APK 资产和版本标签请查看：

- [项目更新日志](../website/update/history.mdx)
- [GitHub Releases](https://github.com/Yizuka17/YumeBox-MaterialDesign/releases)

## 未来计划

后续会继续打磨 MD3 体验、配置编辑、运行状态展示、流量统计、Sub-Store / GeoX 流程和整体稳定性。少量遗留 UI 边界也会逐步收敛，减少对兼容层的直接依赖。

## 反馈与贡献

- Bug 与建议：[Issues](https://github.com/Yizuka17/YumeBox-MaterialDesign/issues)
- 贡献指南：[CONTRIBUTING](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/CONTRIBUTING.md)
- 翻译贡献：Fork 本项目，并在 `locale/lang` 目录下创建或更新对应的翻译文件。
- 讨论与反馈：[@OOM_WG](https://t.me/OOM_Group)

## 特别

本项目基于 [YumeBox](https://github.com/YumeRiMoe/YumeBox) 继续开发，~~作者对这个项目中的代码一无所知。代码处于可用或不可用状态，没有第三种情况。~~ 开发过程中采用 AI 辅助完成部分代码实现、文档整理与问题排查，感谢原项目及相关开源项目的贡献。

以及该项目中使用的 [第三方](https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/docs/ThirdParty.md) 库。


