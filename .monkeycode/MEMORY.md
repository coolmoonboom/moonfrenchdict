# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-08-29
- Context: Discovered by Agent while implementing prebuilt SQLite dictionary database architecture
- Category: Build Methods
- Instructions:
  - Dictionary database rebuild: run `python3 tools/build_dict_db.py` (input `tools/data/word.sj`, output `android/app/src/main/assets/dictionary.db`). This regenerates the 3-gram inverted index, so after changing build_dict_db.py the db MUST be regenerated before building the APK.
  - `word.sj` is the build source and must NOT be placed back into `app/src/main/assets/` (it would bloat the APK). It lives in `tools/data/`.
  - dict_ngram stores posting lists as 4-byte big-endian unsigned int blobs (struct.pack('>I')), matching the Kotlin `decodeInt32` in DictRepository.
  - Fuzzy search uses shared-gram threshold >= 3 (MIN_SHARED_GRAMS) over the 3-gram union to shrink candidates; empty candidates fall back to full normSet scan.

[Project Knowledge Summary]
- Date: 2026-08-29
- Context: Discovered by Agent while releasing v1.0.5
- Category: Operations & Deployment
- Instructions:
  - GitHub remote: `https://github.com/coolmoonboom/moonfrenchdict.git`, main branch. gh CLI token expires; re-authenticate with `echo -e "protocol=https\nhost=github.com\n" | git credential fill` then `gh auth login --with-token`.
  - Release upload: copy APK to `/tmp` first, then `gh release upload <tag> "/tmp/name.apk#display-name.apk"` — the `#` label only works from a clean path outside the repo.
  - Build command with memory limits: `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon -Dorg.gradle.jvmargs="-Xmx3G -XX:MaxMetaspaceSize=1G -XX:ReservedCodeCacheSize=256m"` (via background terminal).

[Project Knowledge Summary]
- Date: 2026-09-02
- Context: Discovered by Agent while fixing 真机(MIUI14)汇报 nativeInit=0 引擎初始化失败（dlopen 修复后的第二层问题）
- Category: Troubleshooting & Debugging
- Instructions:
  - mimic (HTS) 语音的 voice feature `sample_rate` 在注册时(register_siwis_fr_zoe_hts)被写死为 0：此时引擎未加载，HTS_Engine_initialize 设 condition.sampling_frequency=0，htsvoice 延迟到首次 hts_synth 才加载。nativeInit 读 get_param_int(features,"sample_rate",44100) 返回已存在的 0 值，被误判为初始化失败。修复：nativeInit 里 voice_select 后主动加载 htsvoice 再读真实采样率。
  - Flite_HTS_Engine_load 未标记 MIMIC_CORE_PUBLIC，是隐藏符号，native bridge 不能直接调用，会 dlopen cannot locate。改用 libHTSEngine 导出的 HTS_Engine_load(&flite_hts->engine, ...) 并手动置 flite_hts->is_engine_loaded=1。
  - host 端测试(test_bridge_logic.c)的 native_init 返回固定 1、用 w->sample_rate 取采样率，未暴露 bridge 返回 0 的问题——host 验证必须断言与 Android JNI 完全一致的返回值路径。
  - 用户真机只能看到 Toast，无法直接拿 logcat；Toast 文本就是唯一的诊断通道，必须把具体原因(de+deps)塞进 lastError。

[Project Knowledge Summary]
- Date: 2026-09-02
- Context: Discovered by Agent while fixing 真机(MIUI14/Android13)发音无声 dlopen failed
- Category: Build Methods / Troubleshooting & Debugging
- Instructions:
  - Android 上 native 库 `.so` 的未定义符号只在 DT_NEEDED 声明的库中查找。用 `<android/log.h>` 的 `__android_log_print` 时，编译共享库必须显式加 `-llog`，否则真机报 `dlopen failed: cannot locate symbol "__android_log_print"`（host x86 不报，只有真机 arm64 暴露）。
  - NDK clang 默认链接共享库会引入 `__register_atfork@LIBC` 引用（来自 crtbegin），部分设备 bionic libc 不导出该符号，同样会 cannot locate。消除方法：`-nostdlib` 编译（仅纯 C、无 C++ 构造的 bridge 可安全使用），同时仍显式 `-llog -ldl -lm -lc`。
  - 排查命令：`llvm-readelf -d lib.so | grep NEEDED`、`llvm-nm -D --undefined-only lib.so`、对照 `$NDK/sysroot/usr/lib/<abi>/<api>/libc.so` 的导出符号确认可解析性。
  - 依赖库(ttsmimiccore 等)自身不引用 atfork/android_log，只有 libmimicbridge.so 需修复。
  - 发音引擎 native 链路：libttsmimiccore→libHTSEngine→libpcre2-8→libttsmimic_french→libttsmimic_siwis_fr_zoe_hts→libmimicbridge，bridge 需显式链接全部依赖生成完整 DT_NEEDED。

[Project Knowledge Summary]
- Date: 2026-09-14
- Context: Discovered by Agent while setting up the build environment and compiling the project
- Category: Build Methods / Environment Configuration
- Instructions:
  - The sandbox has no JDK / Android SDK / gradle wrapper by default. `gradlew` and `gradle-wrapper.jar` are gitignored and NOT in the repo; use a system Gradle instead of the wrapper.
  - Build toolchain setup: `apt-get install -y openjdk-17-jdk-headless`; Android cmdline-tools + `platforms;android-36` + `build-tools;36.0.0` installed under `/opt/android-sdk` (accept licenses with `yes | sdkmanager --licenses`); Gradle 9.3.1 downloaded to `/opt/gradle-9.3.1`. `android/local.properties` must contain `sdk.dir=/opt/android-sdk`.
  - Build command (resource-limited background terminal): `cd /workspace/android && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk /opt/gradle-9.3.1/bin/gradle :app:assembleDebug --no-daemon`. Tests: `:app:testDebugUnitTest`. Memory budget: `-Xmx2G`, terminal memory_percent 60 (peak ~4.1G).
  - Large assets are Git LFS tracked: `android/app/libs/*.aar`, `assets/dictionary.db`, `assets/tts/**/*.onnx`, `tools/data/word.sj`, `dist/*.apk`. Fresh clones contain 133-byte pointers.
  - `git lfs pull` only restored `sherpa-onnx-1.13.7.aar` and `word.sj` from the LFS remote; `dictionary.db` and the TTS onnx were absent on the LFS server and had to be rebuilt: DB via `python3 tools/build_dict_db.py` (input `tools/data/word.sj`, output `assets/dictionary.db`, ~62MB); TTS model downloaded from sherpa-onnx release `vits-piper-fr_FR-siwis-medium.tar.bz2` and extracted `fr_FR-siwis-medium.onnx` into `assets/tts/fr_FR-siwis-medium/`.

[Project Knowledge Summary]
- Date: 2026-08-30
- Context: Discovered by Agent while fixing AI 回复 Markdown 表格/引用块渲染为原始文本
- Category: Troubleshooting & Debugging
- Instructions:
  - AI 回复用 mikepenz multiplatform-markdown-renderer-m3 0.33.0 + markdown-jvm 0.7.3 渲染。markdown-jvm 0.7.3 的表格必须是规范 GFM（表头 + 分隔行 `|---|---|` + 前后空行独立成段）；缺分隔行或表格前带文本会降级为 PARAGRAPH 并原样保留 `|` 字符。
  - 引用块 `>` 行后若无空行直接接普通文本，后续内容会被 lazy continuation 吞入引用块，导致 `>` 与 `|` 同时以原始文本显示。
  - 修复策略：AIClient 的 system prompt 强制 AI 输出规范 GFM；渲染前用 `MarkdownSanitizer.sanitize()` 容错（补分隔行、引用块后补空行），在 AIScreen.kt 和 AIFavoriteDetailScreen.kt 调用。
  - SentenceScreen 的 AI 逐词结果区在 `analysis == null` 时因 `return@Column` 不渲染，导致必须先点"翻译并分析"才能看到 AI 结果；现条件改为 result/aiWords/aiError/aiLoading 任一非空即渲染，并对 result 相关 item 加 null 保护。

[Project Knowledge Summary]
- Date: 2026-09-16
- Context: Discovered by Agent while fixing "换设备后看不到收藏"（云同步）
- Category: Troubleshooting & Debugging
- Instructions:
  - `SyncManager`/`NutsCloudProvider` 用 OkHttp 同步调用；在 Compose `LaunchedEffect`（主线程）里直接调用会抛 `NetworkOnMainThreadException`，而 `download()` 内部 catch 后返回 null，导致自动同步静默失效。所有同步网络调用必须放在 `withContext(Dispatchers.IO)`（`SyncUi.runSync` 已是 IO）。
  - 云同步默认语义应是「合并云端与本地」（并集后回写+上传），不要用覆盖式上传：新设备本机为空时上传会清空云端，导致其他设备随后拉取也变成空。手动「仅上传本机（覆盖云端）」保留为显式破坏性操作。
  - 收藏持久化：单词收藏在 `DictRepository` 的 `favorites` prefs（StringSet），句子收藏、AI 收藏、视频转文字收藏在 `AIPreferences` 的 `ai_settings` prefs；`SyncData`/`SyncBundle` 五段（history/favorites/sentences/ai_favorites/video_texts）须全部打包解包，新增收藏类型时同步扩段。

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered while releasing v1.0.26（发音改进 + APK 瘦身）
- Category: Operations & Deployment / Workflow & Collaboration
- Instructions:
  - Release 版本号与 build.gradle 的 versionName 已解耦：versionName/versionCode 长期停在 1.0.17/17，GitHub release 编号按发布顺序递增（已到 v1.0.25）。新发布号从远端最新 release +1 取（如 v1.0.26），不要按 build.gradle 里的 versionName 推断；releases/tags 需先 `git fetch --tags origin` 再看远端。
  - 仓库 `prepare-commit-msg` hook 会自动为每条提交追加 `Co-authored-by: monkeycode-ai <monkeycode-ai@chaitin.com>`，提交信息里不要再手动写该行，否则会重复两条。
  - `gh release create/upload` 必须在 git 仓库目录内执行（gh 依赖 .git 上下文），APK 资产从 /tmp 用 `path#display-name.apk` 语法引用；APK 用 release 号命名（french_dict_v1.0.26.apk）。
  - 构建 daemon 内存：gradle 堆 -Xmx3G + kotlin.daemon.jvmargs -Xmx768m + `--no-daemon --max-workers=2`，terminal memory_percent 70（peak ~4.5G）可稳定跑完含 R8 的 release 构建；-Xmx2G 或并行 worker 过多会触发 cgroup OOM 杀掉 daemon。

[Project Knowledge Summary]
- Date: 2026-09-20
- Context: Discovered while rebuilding the APK to replace an existing release asset without bumping the version
- Category: Operations & Deployment
- Instructions:
  - 需要「不刷版本号、直接替换最新 release 的包」时：`gh release list` 取 Latest tag（当前 v1.0.26），构建新包后 `gh release upload <tag> "/tmp/<apk-name>.apk" --clobber`。资产名取路径 basename，`--clobber` 覆盖同名旧包；必须在仓库目录内执行，命令成功退出码 0（可能无 stdout）。
  - Release 构建命令：`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk /opt/gradle-9.3.1/bin/gradle :app:assembleRelease --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx3G -XX:MaxMetaspaceSize=1G -XX:ReservedCodeCacheSize=256m" -Dkotlin.daemon.jvmargs="-Xmx768m"`，约 10 分钟；产物 `app/build/outputs/apk/release/app-release.apk`（release 用 debug 签名）。

