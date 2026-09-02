# DayLog · 日报纪念册

DayLog 是一款面向团队管理者的离线 Android 日报收集工具。成员档案、日报、标签和大事记全部保存在手机本地，无需注册、登录或网络。

## 下载

[**下载最新版 DayLog.apk**](https://github.com/MeowuzZ/DayLog/releases/latest/download/DayLog.apk)

Android 8.0 及以上版本可安装。安装新版本时请直接覆盖安装，不要先卸载旧版。

## 主要功能

- 月历日报：按日期查看日报时间轴，月历随上滑平滑折叠为周历。
- 手动录入：日报、成员和大事记页面的右上角均提供简洁的 `+` 入口。
- 90 天奖励统计：同一成员同一天多篇日报只计 1 天，全部内容仍会保留。
- 成员档案：姓名、专业、年级、加入时间、职级和单选标签。
- 职级选项：`T0`、`T1-1`、`T1-2`、`T1-3`、`T2-1`、`T2-2`、`T2-3`、`T3-1`、`T3-2`、`T3-3`。
- 可扩展标签：从已有标签中单选，也可在成员弹窗内新建标签。
- 个人大事记：只需选择日期并填写文本，按最新优先的时间轴展示。
- 团队大事记：独立导航页汇总全部成员的大事记。
- 个人纪念册导出：支持 Word、PDF、Markdown 和纯文本，包含档案、大事记与日报时间轴。
- 完整备份：导出 `.rhb` 文件，重新安装或换机后可导入恢复。

## 数据安全与升级

- 数据库和 App 版本分离，覆盖安装同签名新版 APK 不会清除数据。
- v1 数据库会自动升级到 v2，原有成员和日报保留；原“个人简介”会转换为一条“原个人简介”大事记。
- v1 和 v2 的 `.rhb` 备份均可导入；新备份会包含成员、标签、日报和大事记。
- 卸载 App 会清除本地数据，建议更新或换机前先导出备份。

## 本地构建

需要 JDK 17 与 Android SDK 34：

```bash
./gradlew :app:assembleDebug
```

调试包输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

## 自动化

- 提交到 `main` 或创建 Pull Request 时，`Android CI` 会自动编译、检查并上传调试 APK 作为 Actions 产物。
- 推送 `v*` 标签时，`Publish Android release` 会构建固定签名的 APK，验证签名，并发布为 GitHub Release 的 `DayLog.apk` 及 SHA-256 校验文件。
- Release 工作流需在仓库 Secrets 中配置 `DAYLOG_KEYSTORE_BASE64`、`DAYLOG_KEYSTORE_PASSWORD`、`DAYLOG_KEY_ALIAS` 和 `DAYLOG_KEY_PASSWORD`。

## 技术结构

- Kotlin + Jetpack Compose
- Android `SQLiteOpenHelper` 本地数据库
- Android Storage Access Framework 文件保存/选择
- Android `PdfDocument` 生成 A4 PDF
- Open XML ZIP 结构生成 `.docx`
- JSON + ZIP 生成 `.rhb` 备份

## 验证记录

- 已验证 Android 16.1 模拟器上的 v1→v2 数据库升级，原成员和日报保留。
- 已验证右上角新增入口、职级单选、标签新建和大事记时间轴。
- 月历—周历折叠动画优化后，同一模拟器测试的卡顿帧比例由 28.16% 降至 4.39%。
- 已验证 Markdown、Word、PDF 导出以及 `.rhb` 备份恢复基础流程。

## 许可证

本项目使用 [MIT License](LICENSE)。
