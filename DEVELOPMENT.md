# Doozle Development

## Prerequisites

- NixOS with flakes enabled
- The `flake.nix` provides: Android SDK (platforms 34-35), Gradle, JDK 17, Kotlin

## Dev Commands

```bash
# Enter dev shell (first time downloads ~500MB of SDK)
nix develop

# Inside dev shell (or prefix with `nix develop --command bash -c "..."`)

# Compile everything
gradle :app:compileDebugKotlin

# Run unit tests (B* tree engine, nudge, pair selection)
gradle :app:testDebugUnitTest

# Run a specific test class
gradle :app:testDebugUnitTest --tests "com.goholand.doozle.engine.BStarTreeTest"

# Build debug APK
gradle :app:assembleDebug

# Clean build
gradle clean
```

## Project Structure

```
app/src/main/java/com/goholand/doozle/
├── engine/         # Pure Kotlin: B* tree, nudge, pair selection (no Android deps)
├── data/           # SAF wrappers, project storage, preferences
├── ui/             # Compose UI
│   ├── screens/
│   ├── components/
│   └── theme/
└── di/             # Koin modules

app/src/test/       # Unit tests (JUnit 5, run on JVM)
```

## Architecture Notes

- The B* tree engine is pure Kotlin with a `FileSystem` interface
- Tests use `InMemoryFileSystem`; production uses SAF-backed implementation
- No database; folder structure IS the ordering
