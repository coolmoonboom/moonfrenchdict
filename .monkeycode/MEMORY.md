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
- Date: 2026-08-30
- Context: Discovered by Agent while fixing AI 回复 Markdown 表格/引用块渲染为原始文本
- Category: Troubleshooting & Debugging
- Instructions:
  - AI 回复用 mikepenz multiplatform-markdown-renderer-m3 0.33.0 + markdown-jvm 0.7.3 渲染。markdown-jvm 0.7.3 的表格必须是规范 GFM（表头 + 分隔行 `|---|---|` + 前后空行独立成段）；缺分隔行或表格前带文本会降级为 PARAGRAPH 并原样保留 `|` 字符。
  - 引用块 `>` 行后若无空行直接接普通文本，后续内容会被 lazy continuation 吞入引用块，导致 `>` 与 `|` 同时以原始文本显示。
  - 修复策略：AIClient 的 system prompt 强制 AI 输出规范 GFM；渲染前用 `MarkdownSanitizer.sanitize()` 容错（补分隔行、引用块后补空行），在 AIScreen.kt 和 AIFavoriteDetailScreen.kt 调用。
  - SentenceScreen 的 AI 逐词结果区在 `analysis == null` 时因 `return@Column` 不渲染，导致必须先点"翻译并分析"才能看到 AI 结果；现条件改为 result/aiWords/aiError/aiLoading 任一非空即渲染，并对 result 相关 item 加 null 保护。
