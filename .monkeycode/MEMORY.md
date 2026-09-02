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
- Date: 2026-08-30
- Context: Discovered by Agent while fixing AI 回复 Markdown 表格/引用块渲染为原始文本
- Category: Troubleshooting & Debugging
- Instructions:
  - AI 回复用 mikepenz multiplatform-markdown-renderer-m3 0.33.0 + markdown-jvm 0.7.3 渲染。markdown-jvm 0.7.3 的表格必须是规范 GFM（表头 + 分隔行 `|---|---|` + 前后空行独立成段）；缺分隔行或表格前带文本会降级为 PARAGRAPH 并原样保留 `|` 字符。
  - 引用块 `>` 行后若无空行直接接普通文本，后续内容会被 lazy continuation 吞入引用块，导致 `>` 与 `|` 同时以原始文本显示。
  - 修复策略：AIClient 的 system prompt 强制 AI 输出规范 GFM；渲染前用 `MarkdownSanitizer.sanitize()` 容错（补分隔行、引用块后补空行），在 AIScreen.kt 和 AIFavoriteDetailScreen.kt 调用。
  - SentenceScreen 的 AI 逐词结果区在 `analysis == null` 时因 `return@Column` 不渲染，导致必须先点"翻译并分析"才能看到 AI 结果；现条件改为 result/aiWords/aiError/aiLoading 任一非空即渲染，并对 result 相关 item 加 null 保护。
