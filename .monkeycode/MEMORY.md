# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Entries

[跨平台桌面试水与 CI 出包要点]
- Date: 2026-09-22
- Context: Discovered by Agent while building the :desktop Compose Desktop module and Windows CI packaging
- Category: Build Methods
- Instructions:
  - Android 回归构建（约 10-12 min）：`cd /workspace/android && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk /opt/gradle-9.3.1/bin/gradle :app:testDebugUnitTest :app:assembleRelease --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx3G -XX:MaxMetaspaceSize=1G -XX:ReservedCodeCacheSize=256m" -Dkotlin.daemon.jvmargs="-Xmx768m"`，通过标准 = 128 单测 0 失败 + BUILD SUCCESSFUL
  - 桌面无头自检：`gradle :desktop:run --args="--selftest"`（不建窗口，Linux/CI 可跑；约 6s 加载 dictionary.db 并校验查词/变位/中文候选）
  - Windows 包只能在 Windows runner 上产。.github/workflows/desktop-windows.yml 用 `:desktop:createDistributable`（app-image，免 WiX）出 `desktop/build/compose/binaries/main/app/MoonFrenchDict/`，Compress-Archive 成 zip 上传 artifact
  - **jpackage 陷阱**：`@args.txt` 按 ISO-8859-1 解析，`nativeDistributions` 的 description/vendor 等元数据含中文会让 CI 失败且报错仅 `Input length = 1`（Linux 本地 UTF-8 环境不复现）。保持这些字段纯 ASCII；排查时用 workflow 额外上传 `desktop/build/compose/logs/` 目录
  - GitHub 无 LFS locking server，首次 push tag 会报 locksverify EOF：`git config lfs.<repo>/info/lfs.locksverify false` 后重试
