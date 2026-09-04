# DayLog · 日报纪念册

DayLog 是一款面向团队管理者的离线 Android 日报收集工具。成员档案、日报、标签和大事记全部保存在手机本地，无需注册、登录或网络。

## 下载

[**下载最新版 DayLog.apk**](https://github.com/MeowuzZ/DayLog/releases/latest/download/DayLog.apk)

Android 8.0 及以上版本可安装。安装新版本时请直接覆盖安装，不要先卸载旧版。

### 微信扫码下载

<p align="center">
  <a href="https://github.com/MeowuzZ/DayLog/releases/latest">
    <img src="docs/daylog-download-qr.png" width="420" alt="微信扫一扫，打开 DayLog 最新版下载页面">
  </a>
</p>

二维码始终指向 GitHub 最新版下载页面；使用微信扫一扫后，在页面中点击 `DayLog.apk` 即可下载。

## 更新日志

### v1.4.1

- 回退 v1.4.0 的微信日报导入功能，移除相关入口、无障碍服务和权限声明。
- 新增微信可扫描的圆点二维码，直达 GitHub 最新版下载页面。
- 补充项目版本更新记录。

### v1.4.0

- 曾加入基于微信界面读取的日报导入试验；该功能已在 v1.4.1 回退。

### v1.3.0

- 全面更新粉色水果主题，统一月历、表单、弹窗、文件菜单和底部导航视觉。
- 优化窄屏与大字体布局，保持 Android 8.0 兼容。

### v1.2.2

- Word、PDF、Markdown、纯文本和完整备份生成后可直接唤起微信分享。
- 分享完成或取消后返回原页面，同时保留本地另存入口。

### v1.2.1

- 修复系统返回层级与按 Home 键后重新进入时的页面恢复。
- 修复成员弹窗中新建标签无法弹出的问题。

### v1.2.0

- 新增职级、自定义标签、个人大事记和团队大事记。
- 新增数据库无损升级、完整备份与自动发布流程。

## 主要功能

- 粉色水果主题：浅粉背景、棕红文字、白色描边卡片和珊瑚红按钮，底部水果图标在选中时切换为切面，并提供按下反馈。
- 月历日报：按日期查看日报时间轴，显示农历日期，月历随上滑平滑折叠为周历，也可点击折叠箭头。
- 手动录入：日报、成员和大事记页面提供圆形浮动 `+` 入口，表单、弹窗与文件导出菜单采用统一主题。
- Android 导航：子页面按系统返回键回到上一页；底部导航根页按返回键回到桌面；按 Home 键后再进入会保留离开时的页面。
- 90 天奖励统计：同一成员同一天多篇日报只计 1 天，全部内容仍会保留。
- 成员档案：姓名、专业、年级、加入时间、职级和单选标签。
- 职级选项：`T0`、`T1-1`、`T1-2`、`T1-3`、`T2-1`、`T2-2`、`T2-3`、`T3-1`、`T3-2`、`T3-3`。
- 可扩展标签：从已有标签中单选，也可在成员弹窗内新建标签。
- 个人大事记：只需选择日期并填写文本，按最新优先的时间轴展示。
- 团队大事记：独立导航页汇总全部成员的大事记。
- 个人纪念册导出：Word、PDF、Markdown 和纯文本生成后均直接唤起微信，以文件形式分享，完成或取消后可返回原页面。
- 完整备份：导出 `.rhb` 文件后直接唤起微信分享，也可选择另存到本机或网盘，重新安装或换机后可导入恢复。

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
- Android ICU 计算农历日期；Compose Canvas 绘制水果和线条图标
- Android `PdfDocument` 生成 A4 PDF
- Open XML ZIP 结构生成 `.docx`
- JSON + ZIP 生成 `.rhb` 备份

## 验证记录

- 已验证 Android 16.1 模拟器上的 v1→v2 数据库升级，原成员和日报保留。
- 已验证右上角新增入口、职级单选、标签新建和大事记时间轴。
- 月历—周历折叠动画优化后，同一模拟器测试的卡顿帧比例由 28.16% 降至 4.39%。
- 已验证 Markdown、Word、PDF 导出以及 `.rhb` 备份恢复基础流程。
- v1.2.2 已在 Android 16.1 模拟器上通过测试接收器验证 Word、PDF、Markdown、纯文本及 `.rhb` 附件读取权限、文件内容和返回页面；真实微信发送效果仍需手机确认。

- v1.3.0 已在 Android 16.1 模拟器检查常规与 360 dp / 1.3 倍字体显示，并验证新增日报、月历折叠、月份切换、成员职级选择、大事记录入弹窗、Word 和完整备份分享、本地保存与导入入口。
- v1.4.1 已移除微信日报导入入口及其无障碍服务声明；下载二维码已通过独立识码引擎验证。

## 许可证

本项目使用 [MIT License](LICENSE)。
