# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Entries

[跨平台桌面试水与 CI 出包要点]
- Date: 2026-09-22
- Context: Discovered by Agent while building the :desktop Compose Desktop module and Windows CI packaging
- Category: Build Methods
- Instructions:
  - Android 回归构建（约 10-12 min）：`cd /workspace/android && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk /opt/gradle-9.3.1/bin/gradle :app:testDebugUnitTest :app:assembleRelease --no-daemon --max-workers=2 -Dorg.gradle.jvmargs="-Xmx3G -XX:MaxMetaspaceSize=1G -XX:ReservedCodeCacheSize=256m" -Dkotlin.daemon.jvmargs="-Xmx768m"`，通过标准 = 全部单测 0 失败 + BUILD SUCCESSFUL
  - 桌面无头自检：`gradle :desktop:run --args="--selftest"`（不建窗口，Linux/CI 可跑；约 6s 加载 dictionary.db 并校验查词/变位/中文候选）
  - Windows 包只能在 Windows runner 上产。.github/workflows/desktop-windows.yml 用 `:desktop:createDistributable`（app-image，免 WiX）出 `desktop/build/compose/binaries/main/app/MoonFrenchDict/`，Compress-Archive 成 zip 上传 artifact
  - **jpackage 陷阱**：`@args.txt` 按 ISO-8859-1 解析，`nativeDistributions` 的 description/vendor 等元数据含中文会让 CI 失败且报错仅 `Input length = 1`（Linux 本地 UTF-8 环境不复现）。保持这些字段纯 ASCII；排查时用 workflow 额外上传 `desktop/build/compose/logs/` 目录
  - GitHub 无 LFS locking server，首次 push tag 会报 locksverify EOF：`git config lfs.<repo>/info/lfs.locksverify false` 后重试
  - 工作流里 `gh release create` 需在 yml 顶层声明 `permissions: contents: write`（该仓库 GITHUB_TOKEN 默认只读，否则 403 Resource not accessible by integration）；tag 触发的构建读取 tag 上的工作流文件，改工作流后要重指 tag 才生效
  - **Android APK 发布约定**：Release 编号 v1.0.x 每发一次 +1，与 `app/build.gradle.kts` 的 versionName 脱钩（versionName 保持 1.0.26 未动）；产物名固定 `french_dict_vX.Y.Z.apk`。手动发布：先把 `app/build/outputs/apk/release/app-release.apk` 复制成该名，再 `gh release create vX.Y.Z --target main --title <中文标题> --notes <中文说明> /tmp/opencode/french_dict_vX.Y.Z.apk`（新 release 会顶成 Latest）
  - `gh release upload` 的 `path#name` 重命名语法不可靠（曾把资产传成 app-release.apk）；删资产要用数字 id（`gh api -X DELETE .../releases/<release_id>/assets/<asset_id>`），172MB 资产上传/创建 release 用后台终端执行避免超时

[本机构建内存配额]
- Date: 2026-09-24
- Context: Discovered by Agent while running :app:testDebugUnitTest + :app:assembleRelease in one background terminal
- Category: Build & Compilation
- Instructions:
  - 主机总内存 ~8GB。同一后台终端连跑单测+R8 打包时 memory_percent 必须给 70（55 会在 minifyReleaseWithR8 阶段被 cgroup OOM 杀掉，日志只报 "Gradle build daemon disappeared"）；仅 compileDebugKotlin 用 55 即可
