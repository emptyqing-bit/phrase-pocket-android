# Phrase Pocket for Android（随手收 · 安卓版）

桌面版"随手收"的 Android 原生实现（MVP）。刷视频时把想学的英文表达或备忘通过系统分享保存到收藏，主屏复习卡片帮你反复记忆。

## 功能（MVP）

- **分享保存**：在任意 App（视频、浏览器、聊天）选中文字 → 分享 → 随手收，选择"单词/备忘"保存，可编辑后再存
- **悬浮球速记**：菜单开启悬浮球（需悬浮窗权限），小球半透明缩在屏幕边缘、可拖动，点一下展开输入框，选单词/备忘即存即走；通知栏可关闭
- **复习模式**：SM-2 简化间隔重复算法，到期单词自动进入复习队列，显示释义后按"认识/模糊/不认识"自评，系统自动安排下次复习（参考 Anki/墨墨的核心机制）
- **TTS 发音**：收藏列表和复习页均可朗读英文（系统 TTS，离线可用）
- **收藏管理**：单词 / 备忘两个收藏夹，搜索、编辑、长按删除
- **备份/恢复**：整库 JSON 备份与合并恢复（按类型+内容去重），也是与桌面版互导的通道
- **离线词典**：内置小词表（25 条示例词条）；支持导入完整 ECDICT sqlite 词典（`stardict` 表），编辑时点"查释义"自动补全
- **可选 AI 释义**：配置自己的 OpenAI 兼容接口（在线 API 或本地 Ollama）后，编辑页出现"AI 释义"按钮，自动生成释义+例句。**使用你自己的接口和额度，App 本身免费、无内购**
- **主屏复习卡片**：桌面小组件随机展示收藏，点"换一条"刷新，每 30 分钟自动轮换，点击卡片进入 App
- **Anki 导出**：把带释义的单词导出为 Anki 可导入的 TSV
- **隐私**：全部收藏保存在本机；仅在主动配置并使用 AI 功能时才发起网络请求，不读取其他 App 内容

## 与桌面版的差异

Android 不允许读取其他 App 的字幕，因此"取当前字幕"改为系统分享/复制保存。桌面常驻卡片对应主屏小组件，悬浮球则提供更贴近桌面版的随时速记体验。

## 技术栈

Kotlin · Room · ViewBinding · Material 3 · AppWidget · Storage Access Framework
最低 Android 8.0（API 26），目标 Android 14（API 34）。

## 构建

环境要求：JDK 17、Android SDK（platform 34 + build-tools）。

> 注：GitHub 仓库未包含 Gradle wrapper 脚本与 jar。最简单的方式是用 **Android Studio 打开本目录**，它会自动补齐 wrapper 并构建；也可以在本地执行 `gradle wrapper` 后使用 `gradlew assembleDebug`。

```bash
# Windows
ggradlew.bat assembleDebug
# macOS / Linux
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 导入完整词典

1. 从 [ECDICT 发布页](https://github.com/skywind3000/ECDICT/releases/tag/1.0.28) 下载 `ecdict-sqlite-28.zip` 并解压，把 `.db` 传到手机
2. App 右上角菜单 → 导入 ECDICT 词典 → 选择该文件
3. 编辑收藏时点"查释义"即可离线查询

## 与电脑端互通

JSON 备份格式与浏览器扩展一致（`kind` / `text` / `meaning`，ISO 时间）：扩展"备份与导出"的 JSON 可在 Android 菜单"恢复备份"中合并导入，Android"备份收藏"的 JSON 同样可在扩展中合并导入，均按类型+内容去重。

## 路线图（后续版本）

- 复习统计与每日目标（连续打卡、掌握度曲线）
- 与桌面版收藏互导（备份文件合并去重，JSON 格式已对齐）
- 选中文字直接保存（ACTION_PROCESS_TEXT）
- 例句 AI 生成进入复习卡片背面
