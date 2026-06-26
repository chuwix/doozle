# Doozle

Photo ranking app for Android. Organizes photos in a folder by pairwise comparison, using a B* tree as the ranking data structure backed directly by the filesystem.

## How It Works

You select a folder of photos. The app presents pairs of photos and asks "which is better?" Based on your choices, photos get inserted into a ranked B* tree structure stored as numbered files in a `_ranked/` subdirectory. Any photo not yet in `_ranked/` is "unseen" and gets priority in pair selection.

The folder structure IS the database. No SQLite, no Room. The position of each photo is encoded in its filename prefix (`0_sunset.jpg`, `1_beach.png`, ...) within B* tree leaf directories.

## Key Concepts

- **Project** = a folder path on device (selected via SAF)
- **Unseen** = any image file in the project folder (recursive) not inside `_ranked/`
- **Ranked** = photos in the `_ranked/` B* tree structure
- **Comparison** = one pairwise "A or B?" judgment that nudges positions

## B* Tree (m=10)

| Property | Value |
|----------|-------|
| Order (m) | 10 |
| Max keys per leaf | 9 |
| Min keys per leaf | 6 |
| Max children per internal node | 10 |
| Min children per internal node | 7 |
| Split strategy | 2-to-3 (two full 9-key leaves -> three 6-key leaves) |

Root children are exempt from minKeys validation (after first root split, children have 5 each).

## Nudge Algorithm

When A wins over B:
- `delta = max(1, totalPhotos / nudgeDivisor / 2)`
- Winner moves up (toward position 0) by delta
- Loser moves down by delta
- Unseen boost: 2x multiplier on first comparison (configurable)

## Tech Stack

- Kotlin, Jetpack Compose, Material 3 (dark theme only)
- Android min SDK 30, target SDK 35
- SAF (Storage Access Framework) for file access
- Koin for DI
- Coil for image loading
- JUnit 5 for unit tests
- NixOS flake for reproducible dev environment

## Package

`com.goholand.doozle`
