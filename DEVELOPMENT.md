# Doozle Development

## Prerequisites

- NixOS with flakes enabled (or any system with `nix` and flakes)
- The `flake.nix` provides: Android SDK (platforms 34-35), Gradle, JDK 17, Kotlin, android-tools (ADB)
- All nix flake files must be `git add`'ed for `nix develop` to work

## Dev Commands

```bash
# Enter dev shell
nix develop

# Or prefix commands directly:
nix develop --command bash -c "gradle :app:testDebugUnitTest"

# Compile
gradle :app:compileDebugKotlin

# Run all unit tests
gradle :app:testDebugUnitTest

# Run specific test class
gradle :app:testDebugUnitTest --tests "com.goholand.doozle.engine.BStarTreeTest"

# Build + install to connected device
gradle :app:installDebug

# Clean
gradle clean
```

## Deployment (Wireless ADB)

Target device: Samsung S23 Ultra (`s23` on Tailnet).

```bash
# Connect (get port from Settings > Developer Options > Wireless Debugging)
adb connect s23:<port>

# If first time, pair first:
adb pair s23:<pair_port> <pairing_code>

# Install
gradle :app:installDebug

# Force restart app
adb -s s23:<port> shell am force-stop com.goholand.doozle
adb -s s23:<port> shell am start -n com.goholand.doozle/.MainActivity

# View logs
adb -s s23:<port> logcat -d | grep -E "ComparisonVM|SafFileSystem|Doozle"

# Clear logs then capture fresh
adb -s s23:<port> logcat -c
```

There's also `scripts/adb-connect.sh` for interactive connection (defaults to host `s23`).

**Secure Folder (user 150):** ADB cannot install directly (SecurityException). Must clone app from main profile via phone's Secure Folder UI.

## Project Structure

```
app/src/main/java/com/goholand/doozle/
├── engine/              # Pure Kotlin engine (no Android deps)
│   ├── BStarTree.kt     # Core B* tree with file-based leaves (~950 lines)
│   ├── FileSystem.kt    # FS abstraction interface
│   ├── SafFileSystem.kt # SAF-backed implementation (with dir cache)
│   ├── UnseenManager.kt # Finds unseen photos, promotes to tree
│   ├── PairSelector.kt  # Picks comparison pairs (unseen priority, neighbor bias)
│   └── Model.kt         # Photo, EngineConfig data classes
├── data/                # Persistence
│   ├── Project.kt       # Project model (kotlinx.serialization)
│   └── ProjectRepositoryImpl.kt  # JSON file storage
├── ui/screens/
│   ├── picker/          # Project list + SAF folder picker
│   └── compare/         # Comparison screen + ViewModel
├── di/AppModule.kt      # Koin DI wiring
├── DoozleApp.kt         # Application class (Koin init)
└── MainActivity.kt      # NavHost, permission check, theme

app/src/test/java/com/goholand/doozle/engine/
├── InMemoryFileSystem.kt    # Test FS implementation
├── BStarTreeTest.kt         # 34 tests
├── NudgeAlgorithmTest.kt    # 14 tests
├── PairSelectorTest.kt     # 12 tests
├── UnseenManagerTest.kt    # 16 tests
├── BStarTreeDebugTest.kt   # 3 diagnostic tests
└── data/ProjectRepositoryTest.kt  # 11 tests
```

## Architecture

### Engine Layer (pure Kotlin, testable)

The B* tree engine operates on a `FileSystem` interface:
- `InMemoryFileSystem` for unit tests (instant, deterministic)
- `SafFileSystem` for production (Android SAF with DocumentFile)

`SafFileSystem` caches `isDirectory` status from `listChildren()` calls to avoid N+1 SAF queries when iterating children.

### Data Flow

1. User selects folder via SAF -> URI persisted with `takePersistableUriPermission`
2. Project stored as JSON in app private storage (kotlinx.serialization)
3. On project open: `UnseenManager.initialize()` ensures `_ranked/` exists
4. `PairSelector.listUnseen()` finds images not in `_ranked/` (cached after first scan)
5. Pair presented -> user picks winner -> `tree.applyComparison()` nudges positions
6. Unseen photos promoted via `insertAtCenter()` on first comparison

### Threading

All file I/O runs on `Dispatchers.IO` via `ComparisonViewModel`:
- Tree initialization
- Unseen scan (can be slow: 2253 files = ~23s via SAF)
- Pair selection (uses cached list after first scan)
- Comparison application (file moves/renames)

UI observes `StateFlow<ComparisonState>` and renders accordingly.

## Invariants

### B* Tree
- Leaf filenames: `{position}_{originalName}` (e.g., `0_sunset.jpg`)
- `stripPrefix` only strips if text before first `_` is a valid integer
- 2-phase rename during rewrite: all to `.t*` temp names, then to final (avoids collisions)
- `redistributeLeaves`/`split2To3Leaves`: `moveAllToTemp` renames in-place; `clearAllFiles` must NOT follow
- Root children exempt from minKeys after first split (5 < 6 is OK for root's children)

### SAF / Permissions
- Tree URI stored as `uri.toString()` (encoded form with `%3A`, `%2F`)
- NavController auto-decodes route params; do NOT add extra `Uri.decode()`
- Permission check compares `perm.uri.toString() == storedUri` (string equality)
- Permissions survive reboots but NOT app reinstalls (re-select folder needed)

### Unseen Logic
- "Unseen" = any image in project folder (recursive) NOT inside `_ranked/`
- No separate `_unseen/` directory; photos stay in place until promoted
- Image extensions: jpg, jpeg, png, webp, heic, heif, bmp, gif
- Hidden files (`.` prefix) excluded
- Staging files (`.staging_*`) excluded from unseen (hidden prefix)
- `PairSelector` caches unseen list; `removeUnseen(path)` called after promotes

### FileSystem Interface
- `parent("photo.jpg")` returns `""` (not `"photo.jpg"` — no slash means root level)
- `fileName("")` returns `""` 
- `InMemoryFileSystem.delete` is recursive (removes dir + all contents)
- `createDirectory` creates all parent dirs (like `mkdir -p`)

## Known Issues

- Full unseen scan is slow on large SAF folders (#1)
- Photos not rendering in comparison view (#3)
- See GitHub issues for full list
