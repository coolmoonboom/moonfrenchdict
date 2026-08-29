# 酷月法语

一个离线优先的法语学习与词典 Android 应用。基于 Compose 构建，内置本地词典、动词变位、句子分析、语法练习与 AI 辅助功能。

## 功能特性

- **查词**：本地词典（约 8000+ 词条）即查即得，支持模糊匹配、近似词/变体推荐、词根拆解（前缀/词根/后缀）、联网释义兜底
- **动词变位**：输入动词原形生成完整变位表，覆盖主动/被动/代动词各时态
- **动词分组**：按词尾与词族分类记忆不规则动词
- **句子分析**：逐词解析句子的词性、时态、语法成分
- **语法练习**：时态、词汇、介词、连词、副词、代词六大题库测验
- **代词表**：常用法语代词速查
- **收藏夹**：本地收藏单词/句子，支持笔记编辑
- **AI 助手**：内置对话界面（需自备 API Key 配置），支持历史会话、导出分享
- **OCR 识别**：图片文字识别基于 Google ML Kit，离线可用

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room / SQLite 本地存储
- Google ML Kit（文字识别）
- OkHttp（联网翻译与 AI 请求）
- PDFBox（收藏导出）
- 离线词典数据内置（`assets/`）

## 构建

环境要求：JDK 17+，Android SDK 34+（compileSdk 36）。

```bash
# 构建 Debug APK
cd android
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

## 安装

- 直接下载 [Release v1.0.2](https://github.com/coolmoonboom/moonfrenchdict/releases/tag/v1.0.2) 中的 APK 安装
- 或按上方步骤本地构建后安装
- 最低支持 Android 7.0（minSdk 24）

## 项目结构

```
android/
├── app/src/main/java/com/coolmoonfrench/dict/   # 应用源码
│   ├── MainActivity.kt                          # 入口与主界面导航
│   ├── LookupScreen.kt                          # 查词
│   ├── ConjugationScreen.kt                     # 动词变位
│   ├── SentenceScreen.kt                        # 句子分析
│   ├── GrammarPracticeScreen.kt                 # 语法练习
│   ├── AIScreen.kt                              # AI 助手
│   └── ...                                      # 其余功能模块
├── app/src/main/assets/                         # 离线词典数据
└── app/src/test/                                # 单元测试
```

## License

本项目代码仅用于学习交流。
