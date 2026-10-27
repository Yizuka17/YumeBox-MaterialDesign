# 上游整合与实机验收（2026-10-27）

当前为 `0.6.5-preview.1` 试用源码，工作分支 `codex/upstream-release-core`。按维护者要求不创建发布标签、不发布 GitHub Release，待持续使用确认稳定后再发布正式版。

## 整合范围

- 上游：`YumeYucca/YumeBox` 的 Moe `6467ae53`（2026-10-24）。上游重写过历史；本 fork 的 `8e7ebe6d` 与上游 `04706810` 的 Git tree 完全相同，通过合并父提交记录这个共同基础。
- 保留 fork 的源代码命名空间 `com.github.yumelira.yumebox` 和应用 ID `com.github.yizuka17.yumebox.md3`。
- 保留原有 ACG 首页、导航、页面布局、MD3 主题和组件。仅适配上游运行服务接口；新增功能使用现有设置项和原更新弹窗。
- 节点与代理组卡片按维护者后续要求改为两列瀑布流：卡片随内容自然增高，横向、同列纵向间隔均为 12 dp；保留分组切换、排序和分组滚动位置。
- 整合上游独立内核进程、控制器接口、Rust 配置编译器及 Kotlin Toolchain 构建流程。运行模式采用上游的 VPN、Root TUN、eBPF；旧的独立 HTTP 运行模式不在此次上游运行架构中。
- Mihomo 固定到 Alpha `5b79f3c367b964ca951936f13298fd147317060f`（2026-10-26），替换原 `1.19.16`。保留 Android UID 和嵌入规则补丁。
- 将原工作区未提交的 relay 清理逻辑移植到 Rust 编译器，并保留结构化编辑器中的兼容处理。原工作区及其修改未被覆盖。

## #3 与升级兼容

旧版 `0.5.5-alpha` 在 `eb812f66` 上复现：连接详情的 `LazyColumn` 嵌套在外层纵向滚动容器中，因无限高度约束崩溃。保留原底部弹窗，关闭此处外层滚动，由详情列表负责滚动。

旧覆写 JSON、预设绑定、自定义分流迁移到新 YAML 存储，保留源文件与未知字段；保留绑定的禁用状态，并在实际编译路径中遵守该状态。迁移在预览、自动启动之前完成，损坏的源数据不会被空索引覆盖。

流量计数导入 Room，保留旧 MMKV 数据并避免重启时重复导入。设置备份升级到格式 4，兼容旧版 VPN/Root TUN 枚举，保留超级岛设置。

## 正式更新检测

直接读取本 fork 的 GitHub Releases。正式版使用 `/releases/latest`，可选择包含预发布；前台自动检查间隔 6 小时，也可手动检查。显示发布说明和发布说明中的封面，匹配实际 APK 资产，缺少兼容资产时打开发布页。

下载支持进度、取消和重试；检查文件长度、GitHub 提供的 SHA-256、应用 ID、递增的 Android 版本号及签名后再交给系统安装器。未授予安装来源权限时，引导授权并保留已验证安装包。

参考：`bggRGjQaUbCoE/PiliPlus/lib/utils/update.dart` 和 `lib/http/api.dart`。实现为 Kotlin，并复用 fork 原有更新弹窗。

## 验证记录

自动实机检查共 **52 项通过**：版本/预发布筛选、忽略与手动检查、错误响应、断网、取消、下载清理、校验和、APK 身份/版本/签名、安装器跳转、覆写迁移及实际编译链、设置备份恢复、流量迁移、真实 Geo 数据校验。测试使用独立夹具；没有通过测试开关修改正式应用的更新行为。

Rust 编译器库测试 **41 项通过**；JNI 导出检查 **14 项通过**，覆盖加载器、运行桥接和 Rust 编译器。Debug 和经过裁剪、打包、签名的试用 APK 均完成构建与签名校验。

| 路径 | 类原生 `eb812f66`（Android 16） | 小米 `1a83f2cc`（HyperOS 3 / Android 16） |
| --- | --- | --- |
| 覆盖安装、保留旧配置及外观 | Debug 覆盖安装通过 | 使用原签名的压缩打包试用 APK 覆盖安装通过 |
| VPN、实网、启停 | 实际启动/停止/再启动，代理请求 HTTP 204 | 原订阅启动、快捷操作启停、后台保活通过 |
| 节点、代理组 | 节点显示、选择、单节点测速、规则/全局切换通过 | 旧节点和订阅保留、显示正常 |
| 原连接详情 | 活动/已关闭详情、滚动、三次反复打开关闭通过 | 原底部详情弹窗实机显示通过；无新增崩溃 |
| 流量、配置编辑 | 实际统计数据、配置编辑器、实时日志录制及历史日志兼容通过 | 旧数据保留 |
| 更新、备份、Geo 校验 | 52 项自动实机检查通过 | 使用相同实现；更新安装包校验原签名 |
| HyperOS 自启动入口 | 不适用 | 打开系统自启动管理，确认本应用已允许 |
| Shizuku、超级岛 | 不适用 | 授权成功，桌面实见订阅名称及实时流量岛；停止代理后通知撤销，重新启动后恢复 |

Root TUN/eBPF、真实重启后的开机自启动、生物识别解锁及各订阅商的全部协议组合不列为通过；这些需要相应权限、设备条件或持续试用。没有把未执行的路径宣称为“全部功能通过”。

## 构建

使用仓库固定的 Kotlin Toolchain 0.13.0：

```powershell
python scripts/sync_kernel.py
python scripts/native-build.py --all
.\kotlin.bat run -m packaging -- --build-type=debug -Pgeo.bundle=true
.\kotlin.bat run -m packaging -- --build-type=release -Pgeo.bundle=true
```

Release 构建需要本地、Git 忽略的 `signing.properties`，字段为 `keystore.path`、`keystore.password`、`key.alias`、`key.password`。构建支持通过 `YUME_BUILD_JVM_OPTIONS` 传入本地 Gradle JVM 网络参数，不在源码中写入代理地址或签名凭据。

原导航的 Compose Destinations 2.3.0 生成源码保存在 `app/src/navigation-generated`。Toolchain 0.13.0 的 Android KSP 在应用依赖 JVM API 模块时会引用不存在的 `:api:compileAndroidDebug`；当前保留原导航生成结果，避免改变用户界面。新增、删除或修改导航参数时需同步更新这些生成文件，或在工具链修复后恢复生成流程。

JNI 校验：

```powershell
python scripts/tests/check_native_symbols.py
```

设备测试源码在 `scripts/tests/android`，为独立 instrumentation APK，不进入正式应用。测试仅在 Debug 目标上运行；APK 身份与签名反例不会被安装。
