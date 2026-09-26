# 酷月法语

一个离线优先的法语学习与词典 Android 应用。基于 Compose 构建，内置本地词典、动词变位、句子分析、语法练习与 AI 辅助功能。

## 功能特性

- **查词**：本地词典（36 万+ 词条，含 3.4 万+ 中文释义）即查即得，支持模糊匹配、近似词/变体推荐、词根拆解（前缀/词根/后缀）；自动过滤输入的首尾与多余空格；本地完全未收录的词由 AI 生成完整词条（版式与本地词条一致）；本地无中文释义时自动用 AI（AI 界面中配置的模型）翻译，未配置 AI 则回退 MyMemory 免费翻译
- **AI 批量导入收藏**：查词界面搜索框旁的「导入」按钮进入导入页，粘贴任意内容（整份单词表 / 一段文本 / 混排笔记），AI 自动过滤序号、中文注释、标题、人名等无关内容，批量识别其中全部法语词与短语并补全词性、音标、中文释义、例句与例句翻译；长内容自动分批识别、按词去重合并；预览每词仅三框（「单词｜词性｜音标｜释义」合并编辑框 + 法语例句 + 例句中文），修正后一次性导入「收藏-单词」，与收藏词一样支持选择与播放
- **输入交互**：进入查词 / 变位界面自动弹出输入法开始搜索；搜索框双击全选（单行选词、多行选句）并聚焦弹出输入法；文本复制 / 剪切 / 粘贴统一使用系统原生长按选择工具条
- **动词变位**：输入动词原形生成完整变位表，覆盖主动/被动/代动词各时态
- **阴阳配合**：输入任意法语词（代词、形容词、名词、冠词等），列出该词全部阴阳单复形态，每个形态附本地例句；配合页底部「动词派生 (AI)」输入动词原形，AI 展开现在/过去分词（含 le/la/les 性数配合与复合时态例句）、动作名词（附「le+动词原形」古体与现代派生名词的语体对比）、施动者名词、形容词（-ant「令人…」与过去分词「感到…」的对立）、副词等整张词形网络
- **动词分组**：按词尾与词族分类记忆不规则动词（-dre/-ttre/-tir/-enir/-indre/-uire/-aître/-oir 等规律族）
- **句子分析**：逐词解析句子的词性、时态、语法成分
- **语法练习**：时态、词汇、介词、连词、副词、代词六大题库测验
- **代词表**：常用法语代词速查
- **收藏夹**：AI 收藏 / 单词 / 句子 / 视频转文字四类分栏展示，支持朗读、复制、多选播放与移除；「收藏-单词」多选后可一键「整理」：按 10 词/批（失败自动重试、逐词即时写回）交给 AI，改写为「标准缩写词性【v.】+ 纯中文义项 + 音标 + 例句/翻译」统一格式——英文杂义、变位词（如 Ferais 标注为 faire 的条件式）一并转写，原有中文备注保留，AI 未给出中文义的词保留原义
- **悬浮窗**：透明自适应小窗播报单词与例句，可拖动（帧间累加算法，精确跟手不乱飘）、暂停播放、语速/字号/背景透明度（透底~全黑五档）调节；单击锁图标后整窗点击穿透（遮住桌面应用时底层可直接点击），通过通知栏「解锁悬浮窗」解除；收藏词循环播放可揭示并朗读中文释义（仅含中文的释义朗读）
- **AI 助手**：内置对话界面（需自备 API Key 配置），支持历史会话、导出分享
- **OCR 识别**：图片文字识别基于 Google ML Kit，离线可用
- **视频转文字**：选择本地法语视频，ffmpeg 提取音频后由 Vosk 离线识别，结果保存到本地记录，识别文本可收藏并随云同步（内置小模型即开即用，可下载高精度大模型，支持导入本地自选模型、卸载释放空间；识别随界面后台自动取消，可用内存不足时自动提醒）。
- **口语对话**：长按麦克风说法语、松开即发送（无需手动点提交），支持打断；AI 回复由本地语音合成逐句播报。语音识别同样基于 Vosk 离线模型，日常对话与发音练习不依赖网络。

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- Room / SQLite 本地存储
- Google ML Kit（文字识别）
- Vosk（离线语音识别，用于视频转文字与口语对话）+ FFmpegKit（音频提取）
- 本地语音合成：eSpeak / 系统 TTS（单词、句子与口语对话回复播报）
- OkHttp（联网翻译与 AI 请求）
- PDFBox（收藏导出）
- 离线词典数据内置（`assets/`）

## 构建

环境要求：JDK 17+，Android SDK 34+（compileSdk 36），Gradle 9.3.1（见 `gradle/wrapper/gradle-wrapper.properties`）。

仓库未包含 `gradlew` 启动脚本，请使用本机安装的 Gradle 9.3.1+ 执行：

```bash
# 构建 Release APK（使用 debug 签名，便于直接安装）
cd android
gradle :app:assembleRelease

# 构建 Debug APK
gradle :app:assembleDebug

# 运行单元测试
gradle :app:testDebugUnitTest
```

APK 输出路径：`android/app/build/outputs/apk/release/app-release.apk`、`android/app/build/outputs/apk/debug/app-debug.apk`

## 安装

- 直接下载 [最新 Release](https://github.com/coolmoonboom/moonfrenchdict/releases/latest) 中的 APK 安装（当前 v1.0.50）
- 或按上方步骤本地构建后安装
- 仅提供 arm64-v8a，最低支持 Android 7.0（minSdk 24）

## 项目结构

```
android/
├── app/src/main/java/com/coolmoonfrench/dict/   # 应用源码
│   ├── MainActivity.kt                          # 入口与主界面导航
│   ├── LookupScreen.kt                          # 查词
│   ├── ConjugationScreen.kt                     # 动词变位
│   ├── AgreementScreen.kt                       # 阴阳配合
│   ├── ImportScreen.kt                          # AI 批量导入收藏
│   ├── DoubleTapSelectAll.kt                    # 输入框双击全选 + 原生长按工具条
│   ├── FloatingWindow.kt                        # 悬浮窗（拖动 / 播报）
│   ├── SentenceScreen.kt                        # 句子分析
│   ├── GrammarPracticeScreen.kt                 # 语法练习
│   ├── AIScreen.kt                              # AI 助手
│   ├── VideoImportScreen.kt                     # 视频转文字
│   ├── VoskModelManager.kt                      # Vosk 模型管理（内置/下载/导入/卸载）
│   ├── VideoToText.kt                           # ffmpeg + Vosk 识别管线
│   ├── StreamingAsr.kt                          # Vosk 流式麦克风识别（口语对话）
│   ├── VoiceChatScreen.kt                       # 口语对话
│   ├── AiWordSearch.kt                          # AI 补全本地未收录词条
│   ├── IpaText.kt                               # 音标渲染
│   ├── room/                                    # Room 本地记录
│   └── ...                                      # 其余功能模块
├── app/src/main/assets/                         # 离线词典数据
├── app/src/main/proguard-rules.pro              # Release 混淆规则（保留 JNA 等）
└── app/src/test/                                # 单元测试
```

## 词典数据

采用**预编译 SQLite 数据库**（`assets/dictionary.db`，约 62MB），首次启动拷贝到应用目录，后续启动直接打开、零解析。查词时释义由 `zh`/`en`/`pos` 现算组装（`DictEntry.combineMeaning`），不存储冗余拼接列。

性能优化：

- 模糊/近似搜索基于 **3-gram 倒排索引**（`dict_ngram`，posting 存 4 字节定长 ID）缩小候选，再按编辑距离精排
- 启动时把全量 norm 载入内存映射（`normById`/`idByNorm`），ngram 候选直接查内存，避免逐条查库
- 查词、AI 翻译、前缀建议均异步执行，输入带防抖取消，不阻塞 UI

数据源（统一格式 `[word, pos, en, zh]`，36 万+ 词条）：

- 法语-中文释义：开源法汉词典（FreeDict / Stardict 系社区数据）
- 法语-英文释义：FreeDict 词典数据
- 本地无中文释义时：由 AI 界面中用户配置的模型生成中文翻译，未配置时回退 MyMemory 免费翻译

词典数据库由 `tools/build_dict_db.py` 从 `tools/data/word.sj` 构建（含 3-gram 倒排索引，加速模糊/近似搜索）：

```bash
python3 tools/build_dict_db.py
```

## License

本项目代码仅用于学习交流。
