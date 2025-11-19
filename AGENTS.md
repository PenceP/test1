# Repository Guidelines

## Project Structure & Module Organization
`app/` is the single Android TV module. Source lives in `app/src/main/java/com/test1/tv` with `ui/*` fragments/adapters, `data/*` for Retrofit + Room orchestration, `background/` for WorkManager jobs (e.g., `CatalogRefreshWorker`), and `util/` helpers. Layouts, drawables, and strings live in `app/src/main/res`. Track roadmap items in `TODO.md` and load API keys from the root `secrets.properties` consumed by `BuildConfig`.

## Build, Test, and Development Commands
Always invoke Gradle via the wrapper:
- `./gradlew assembleDebug` builds the leanback debug APK and regenerates `BuildConfig`.
- `./gradlew installDebug` deploys to a connected emulator or TV device.
- `./gradlew lint ktlintFormat` runs Android Lint plus ktlint; fix or format before committing.
- `./gradlew test` executes JVM unit tests in `app/src/test`.
- `./gradlew connectedAndroidTest` drives instrumentation specs in `app/src/androidTest`.
- `./gradlew :app:bundleRelease` produces the release bundle once signing files exist.

## Coding Style & Naming Conventions
Stick to idiomatic Kotlin with 4-space indentation, trailing commas for multiline args, and expression bodies only when clearer. Name packages by feature (`ui.home`, `data.repository`), classes in PascalCase, members in camelCase, and XML resources in snake_case (`fragment_home.xml`). Repositories expose `suspend` functions, ViewModels rely on `viewModelScope`, long tasks run through WorkManager, and network or Room calls use `Dispatchers.IO` to keep the main thread free.

## Testing Guidelines
Back business logic with JUnit4 tests in mirrored package paths inside `app/src/test`, mocking network layers via Retrofit stubs or Mockito. Instrument leanback focus handling under `app/src/androidTest` using Espresso and `AndroidJUnitRunner`. Name files `ClassNameTest` or `FeatureBehaviorTest`, target ≥80% coverage on repositories, workers, and adapters, and run both Gradle test tasks before pushing changes that affect APIs, SQL schemas, or focus chains.

## Commit & Pull Request Guidelines
Commits follow the repository’s precedent: imperative subjects describing scope first (“Implement OMDb API integration...”), with optional body bullets for context. Rebase or squash fixups so each commit is cohesive. Pull requests must explain the problem, highlight touched packages (`data`, `ui.details`, etc.), and include verification notes (commands run, screenshots/GIFs, migration steps). Reference issue IDs, surface new secrets or config flags, and update `TODO.md` when the roadmap changes.

## Security & Configuration Tips
Never commit populated `secrets.properties`, keystores, or `local.properties`. Load tokens such as `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`, and `TMDB_API_KEY` locally; fallback defaults should stay empty. Redact API responses in logs, avoid printing bearer tokens, configure WorkManager retries to prevent external abuse, and document new permissions in `AndroidManifest.xml`.
