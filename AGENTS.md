# App4PDA — Agent Instructions

This file is the repository's source of agent instructions. Read the relevant sections before working. Inspect `git status --short`, relevant diffs, recent commits, and the implementation. Preserve unrelated user changes. Report discrepancies between instructions and code. An audit or plan does not authorize implementation.

## Repository rules

- **Never commit or push.** Leave changes in the working tree. Do not stage changes, rewrite history, or run destructive Git commands. Review the final diff and report actual verification, failures, and untested behavior.
- Use native `apply_patch` for substantive edits. Do not create backup copies or PowerShell script files for repository work. Use Spotless/ktfmt for Kotlin, Kotlin DSL, and imports. Follow `.editorconfig`, preserve unrelated XML formatting, and keep local IDE configuration out of Git.
- Prefer small, idiomatic changes. Reuse existing Android, Jetpack, or dependency APIs before adding helpers or libraries. Keep Compose, ViewModels, repositories, persistence, and protocol boundaries separate. Keep product timeouts, retries, rate limits, and cache TTLs in `core/AppPolicy.kt`.
- Keep author-written UI text in `app/src/main/res/values/strings.xml` (Russian) and `app/src/main/res/values-en/strings.xml` (English). Use parameters and plurals where appropriate. Localize user-facing errors and keep technical detail in logs. Source content and protocol values retain their original form.
- Do not add migrations, legacy-format adapters, or automatic destructive database recovery. Keep Room schema export disabled. Change versions, storage keys, and durable formats only within the authorized scope. Cache maintenance must preserve settings, accounts, associations, notifications, and finished downloads.
- Keep credentials, private fixtures, and captured forum HTML out of tracked files. Logs may contain sensitive data. Keep reports, APKs, and snapshots in ignored build directories. Keep user-facing documentation in README and agent guidance here.

## Architecture and product contracts

The project has one Android module. `app` wires navigation, lifecycle, and Koin. `feature` owns screens and feature state. `data` owns Room, Preferences DataStore, and source integrations. `core` contains shared platform, UI, network, file, and policy code. Read the relevant implementation before changing a contract.

### Navigation and installed apps

- Home, Digest, Catalog, Downloads, and Settings each retain a back stack. Reselecting a section returns to its root and scrolls the current page to the top without a network refresh. Honor reduced motion and keep nested titles and Back. Accounts and Hidden apps are directly accessible in Settings. App sorting is under Appearance and defaults to Name. Import and export belong under Data and storage.
- Use the themed Material 3 surfaces, system font, accent preferences, and standard controls. Root destinations omit redundant titles. Preserve mini-card layout and device → 4PDA → Google Play → RuStore version order. A disabled source contributes neither its version row nor updates. Only the device version uses status colors. Keep a single launcher alias active and preserve the selected icon.
- Manual Home refresh uses the Expressive pull indicator and one threshold haptic. Automatic progress scrolls with the cards. Show one indicator per operation and retain visible feedback when motion is disabled.
- Apps contains visible packages with a topic or with forum checks disabled. Topic-less eligible packages enter Pending linking until discovery completes, then Not linked if still unbound. Apps are always visible. Other empty segments disappear without a refresh, and saved selection survives initial loading.
- First discovery binds only a unique exact package-ID match. A completed negative or ambiguous search ends discovery. Failures, quotas, and cancellations leave it pending. Publish results per package and recheck revision, identity, visibility, and source settings before writing. Rescans do not reset discovery. Manual binding, unlinking, and explicitly imported associations complete it. HTTP 404 never unlinks an app. Room owns favorites, which refreshes must preserve.
- Initially hide new system apps only when they lack both a launcher activity and their own icon. Explicit Hide and hidden imports clear association, favorites, and individual source state. Hidden exports contain only package identity and the hidden flag. Hidden apps offer Show only. Pending apps permit viewing and downloads while blocking user changes to visibility, favorites, binding, and source settings. Enforce command restrictions outside the UI too.
- Validate current-format imports before writing. Skip absent packages, ignore retired keys, warn about exported sessions, and report partial outcomes across DataStore and Room. Keep operational cache and check state out of exports.

### Sources and caches

- Load public forum pages anonymously over HTTP. Scope credentials to search, profile, and protected download resolution. Login and CAPTCHA are manual. Keep account-private data out of shared caches and never transfer browser cookies or clearance. Keep the shared persisted Cloudflare pause, Retry-After handling, and request pacing. A challenge does not trigger account rotation or a browser action. Search quotas are per account and automatic rotation follows the user's setting.
- Render forum documents in WebView using local styling and controlled local script. Do not execute site scripts. Preserve vertical document scrolling, horizontal HTML scrollers, and attachments reached through post links.
- Publish valid cached pages before refresh and retain them after transient failures. A confirmed main-document topic 404 hides the topic from Catalog and Digest without changing associations. Missing batch entries and quota failures leave source availability unknown. Only confirmed store pages enable store tabs and menu links. Explicit supported Google Play and RuStore URLs open the selected store directly.
- Preserve RuStore batching, request pacing, persisted cooldown, and reuse of unchanged full pages. Google Play uses the app language and an English fallback only after a successful empty description. Scope results to the request language and prevent obsolete responses from replacing current content.
- Google Play uses Aurora GPlayApi 4 with anonymous in-memory sessions scoped to locale. Use the current device profile, request full details before caching, and share the Play cooldown across metadata, check-in, and delivery. Keep Aurora models inside the Google Play integration. Resolve whole delivery files and preserve supplied checksums.
- Digest reads all continuation pages before selecting the latest intervals. Sort newest first, deduplicate topics across selected intervals within each tab, and show selected empty intervals. Keep both depth choices independent at 1–6. Failed parsing preserves the previous successful cache.
- Keep document caches independent of image settings. `AppImages` owns Coil caches for Compose, gallery, and WebView. Each source uses a limit of 0, 100, 200, 500, or 1000 MiB (default 100). Zero disables its disk image cache. Show measured usage on the settings row. Clearing must drain readers safely, reject stale publication, release busy state, and obey the total budget in `AppPolicy`.
- Recoverable source errors use a common source, description, Retry, and Close. Dismissing an error suppresses repeated feedback from the same operation. Retry obeys quotas. Keep field errors local and distinguish parsing and empty content from network failure.

### Downloads and updates

- Ketch and WorkManager own transfers. The app owns URL resolution, integrity, split groups, and installation. A confirmed request survives navigation and reports its own outcome. Keep transfers on public HTTPS without app-session cookies. Do not add automatic retries over Ketch.
- Downloads accepts any file type. Keep active transfers above finished files and actions scoped to one file or split set. Overall progress represents measured bytes and is hidden if a participating size is unknown. Progress events must not rescan or rehash unchanged files.
- Store pages place a clickable file card between screenshots and description. The card opens confirmation. Forum files remain accessible through attachment links. The Open Downloads decision occurs once per logical download and only Settings changes the saved behavior.
- A split set is one logical transfer and one `PackageInstaller` session. Incomplete cancellation stops all parts and deletes only that delivery's partial files. Exclude unverified files from ready results. Revalidate file existence, supplied size and checksum, and split completeness before installation or export. RuStore uses XXH64 with seed zero. Google Play uses the supplied SHA-256 or SHA-1.
- A RuStore delivery may wrap each APK alongside optimization profiles. Verify the original payload before staging the inner APK for installation or export. Keep the original verified files and discard staging files after use.
- Release self-update checks GitHub's latest stable release API, with `releases/latest` and the tagged asset list as fallback. Use GitHub's APK digest. Ask before downloading and open Android's confirmation only after verifying the checksum, package identity, newer version, and signing identity. Defer the installer until the activity is in the foreground.

## Build and verification

- Use the Gradle wrapper and the checked-in daemon/toolchain settings. Keep invocations sequential, use the settings in `gradle.properties`, and run `gradlew --stop` after checks. Follow the current workflows for hosted runner and SDK setup. Build output is under `app/build/` for module tasks and `build/` for root tasks.
- Run `./gradlew verifyDebug --continue` locally or `gradlew.bat verifyDebug --continue` on Windows. The task runs Spotless, type-aware Detekt, debug Android Lint, JVM tests, and debug assembly. CI runs the same task with build and configuration caches disabled for CodeQL's manual build. Use `actionlint` for workflow changes. Review actual test counts and reports. Avoid baselines, broad suppressions, and blanket keep rules.
- In Android Studio, enable EditorConfig and select Kotlinlang in ktfmt Settings. Match the formatter engine version in the version catalog when possible. Keep automatic Optimize imports off for Kotlin when ktfmt manages imports. Treat IDE quick fixes as suggestions and validate API changes by compiling. The pinned Spotless check defines the final format.
- Use only pure Kotlin/JVM tests in `app/src/test` and synthetic inputs. Do not add regression suites, Robolectric, instrumentation, automated UI tests, or APK fixtures. Resource packaging for Compose Preview is allowed.
- Use only an existing emulator when authorized. Preserve data and update in place. Clean installation, uninstall, and data clearing require explicit authorization. For IDE launches use `io.github.ultrafeed.app4pda.app.MainActivity` because the other launcher alias can be disabled.
- Verify the changed path and its failure cases. For asynchronous work include cancellation and stale results. For UI include relevant themes, motion, scrolling, and accessibility. For networking check cooldown and cached content on failure. For file handling check independent concurrent downloads, split completeness, and invalid integrity metadata when relevant. Report hosted, device, or network scenarios that were not exercised.

## Release

- `app.versionName` and `app.versionCode` in `gradle.properties` define the app version. Stable GitHub release tags use `vMAJOR.MINOR.PATCH`. Increase the version code when preparing an update as authorized.
- Local `:app:assembleRelease` builds unsigned when no release keystore path is provided. For signing use `RELEASE_KEYSTORE_PATH`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Use the existing release identity for upgrades. The debug key is never a release fallback. Release enables code and resource shrinking with name obfuscation disabled. Preserve targeted Retrofit/Ketch and XXHash rules when changing R8 configuration.
- `ci.yml` runs on push and PR. After checks, default-branch pushes and manual releases build the `signed-apk` artifact in the `release` environment. Configure `RELEASE_KEYSTORE_BASE64` and the three password/alias secrets there. `release.yml` is dispatched from the default branch. It reuses CI for that commit and creates a draft with the signed APK. Publishing the draft makes it available for self-update. Validate hosted signing, release creation, and device self-update independently of local builds.

## Audits

For each substantive finding, cite code evidence and explain impact, priority, effort, regression risk, the smallest corrective change, and acceptance criteria. Distinguish confirmed problems from assumptions.
