# 跨平台移植可行性分析（Windows / Linux x64 与 arm64）

日期：2026-09-22

## 结论

可行。本项目核心逻辑（变位引擎、IPA 推导、Markdown 解析、中文动词查询、词典查询算法）均为纯 Kotlin，零 Android 依赖，天然可移植。推荐路线为 **Compose Multiplatform Desktop（Kotlin/JVM）**，目标优先级：Windows x64 / Linux x64 优先交付，两个 arm64 平台二阶段验证。

## 分层复用盘点

| 模块 | 现状 | 桌面移植成本 |
|---|---|---|
| VerbConjugator / FrenchIpa / VerbUsages / Morphology | 纯 Kotlin | 零，直接复用 |
| ChineseVerbSearch（Ranker/Parser/Merger） | 纯 Kotlin | 零 |
| MarkdownModel + PDF/bitmap 导出 | 解析纯 Kotlin；渲染用 `android.graphics.Canvas` | 换成 Skiko/AWT 绘制，API 语义对应，小 |
| DictRepository / dictionary.db | 标准 SQLite 数据 + `android.database.sqlite` + assets | 换 sqlite-jdbc，查询逻辑不动，小 |
| AIPreferences / Sync | SharedPreferences / HTTP | JSON 配置文件 + 同样 HTTP，小 |
| AIClient / MyMemory（网络） | JVM HTTP | 基本不动 |
| Compose UI（约 15 个 Screen） | androidx.compose.* | 换 multiplatform 坐标后约 95% 直接复用；Scaffold/LazyColumn/Material3 桌面端全支持，中文 IME 新版 Compose 已支持 |
| TTS（Espeak.kt）+ ASR（StreamingAsr.kt） | sherpa-onnx Android AAR + Vosk Android 绑定 | 两家均有 Java 桌面发行版（Windows dll / Linux so）；Piper 与 Vosk 模型文件跨平台不变；中等工作量，集中在各 ABI 原生库随包分发 |
| Speech（系统 TTS 通道）、文件分享 Intent | Android 独有 | 桌面改走 sherpa TTS 兜底 + 本地文件导出 |

## 平台支持矩阵

| 目标 | 支持度 | 说明 |
|---|---|---|
| macOS arm64 | 官方支持 | 顺带收益 |
| Windows x64 | 官方支持 | jpackage 出 msi/exe |
| Linux x64 | 官方支持 | deb / AppImage |
| Windows arm64 | 实验 | 官方打包矩阵未覆盖；可经 Win11 ARM Prism 跑 x64 包，性能可接受 |
| Linux arm64 | 实验 | JVM（JBR aarch64）可用；Skiko 有 arm64 运行时但官方标实验，需自行验证打包 |

## 实施路径

1. **第一步（低成本）**：新建 `:desktop` 模块，sourceSet 直接引用现有核心代码，仅编写少量 JVM 适配器（sqlite-jdbc、settings、TTS/ASR 原生库加载），用 Compose Desktop 跑通并出 x64 安装包。
2. **第二步**：把 `Speech`/`Espeak`/ASR/Share 抽成接口（expect/actual 或简单 DI），Android 端实现不变，桌面端接 Java 版 sherpa-onnx 与 Vosk。
3. **第三步**：Windows arm64 / Linux arm64 原生打包验证与交付。

## 主要风险

- **原生库分发**：onnxruntime、libvosk 各平台 so/dll，连同 espeak-ng-data 需分平台打包（模型资产 `fr_FR-siwis-medium`、Vosk 小模型均跨平台共用）。
- **arm64 官方支持度**：上表实验项需 spike 验证。
- **安装包工程**：图标、单实例、自动更新、65MB 资产打包（jpackage resources），属标准工作量。

## 备注：与 Apple 生态的关系

若未来需要 iOS/macOS 一等公民体验，可将纯逻辑层升级为 KMP（Kotlin/Native 出 XCFramework）+ SwiftUI 原生界面的混合架构，现有 Android/桌面代码全部保留，与本文路线共享同一套接口抽象。
