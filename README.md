# Architecture Playground

An Android sample app built one lesson at a time — **one lesson, one commit**.
Each commit is self-contained: it introduces a single topic, keeps the app compiling, and
documents the reasoning in KDoc next to the code.

The app being built is a **GitHub repository browser**: Compose UI, Coroutines/Flow,
Retrofit, Hilt, Room, Paging and a full test suite.

## Lessons

| # | Lesson | Topics | Status |
|---|---|---|---|
| 00 | Skeleton | Gradle KTS, version catalog, Compose, wrapper | ✅ |
| 01 | Kotlin deep dive | sealed interface, variance, value class, inline/reified, contracts, DSL | ✅ |
| 02 | Coroutines | suspend, structured concurrency, dispatchers, cancellation | ✅ |
| 03 | Flow | cold vs hot, operators, StateFlow/SharedFlow, flowOn/buffer | ✅ |
| 04 | Retrofit + OkHttp | kotlinx.serialization, interceptors, error mapping | ✅ |
| 05 | Repository + domain | layering, dependency inversion, use cases, composition root | ✅ |
| 06 | Hilt | modules, scopes, qualifiers, multibinding | ✅ |
| 07 | MVVM + Compose state | UiState, unidirectional data flow, reducer, side effects | ✅ |
| 08 | Room | offline-first, single source of truth, migrations | ✅ |
| 09 | Paging 3 | RemoteMediator, Compose integration | ⬜ |
| 10 | Unit testing | MockK, Turbine, runTest/TestDispatcher, fakes | ⬜ |
| 11 | UI testing | Compose test, Hilt test modules, robot pattern | ⬜ |
| 12 | Multi-module | :core / :feature split, build-logic convention plugins | ⬜ |
| 13 | Advanced async | channelFlow, custom operators, WorkManager, retry | ⬜ |
| 14 | Kotlin performance | inline/value class trade-offs, KSP | ⬜ |
| 15 | CI + code quality | GitHub Actions, detekt/ktlint, coverage, R8 | ⬜ |
| 16 | Runtime performance | Compose stability, baseline profiles, measurement | ⬜ |

## Highlights so far

- `AppResult<out T>` — a sealed result type with covariance and `Failure : AppResult<Nothing>`,
  rethrowing `CancellationException` instead of swallowing it.
- `SearchQueryBuilder` — a `@DslMarker` type-safe builder for GitHub search syntax.
- `AppDispatchers` — dispatchers behind an interface so they can be swapped in tests.
- `CoroutineLab` — seven runnable demos (parallelism, cooperative cancellation,
  `NonCancellable` cleanup, `coroutineScope` vs `supervisorScope`, timeouts, dispatcher
  switching) with a live on-device log showing thread names and timings.
- `FlowLab` — seven more demos covering coldness, `transform`, `flowOn`, the
  buffer/conflate/collectLatest trade-off, `combine` vs `zip`, `retryWhen` backoff and
  `shareIn`.
- `RepoRepository` in `domain`, `DefaultRepoRepository` in `data` — dependency inversion,
  so the domain compiles without knowing Retrofit or GitHub exist.
- `SearchRepositoriesUseCase` — validation and ranking rules in one testable place.
- Room as the **single source of truth**: the network only writes, the UI only reads
  storage, so the screen works offline and two observers can never disagree. Freshness is a
  `fetched_at` column, not a guess.
- Two committed schema versions and a real `Migration` between them, with a
  `MigrationTestHelper` test that validates the result against the exported JSON.
- `feature/search` — the UDF contract written down (state down, events up, effects
  sideways once), state produced by a `scan` reducer, `SavedStateHandle` for process death,
  effects on a `Channel`, and a stateless screen with five previews.
- `UiText` — the ViewModel describes text, the composable resolves it; no `Context` above
  the UI layer.
- Hilt graph — `@Binds` vs `@Provides`, qualifiers for three dispatchers and for the
  offline repository, and `@IntoSet` multibinding so interceptors contribute themselves to
  the shared OkHttp client.
- `AppError` + `safeApiCall` — every Retrofit/OkHttp/serialization failure is mapped to a
  typed error at the edge of the data layer, so no network type reaches the UI.
- `NetworkFactory` — a single OkHttp client and Retrofit instance, interceptors in the
  right order, optional token from `local.properties`.
- `FlowLabViewModel` — search-as-you-type as a single declarative chain
  (`debounce` → `distinctUntilChanged` → `flatMapLatest` → `catch` → `stateIn`), with
  one-off events on a `SharedFlow` kept separate from screen state.

## Package layout

```
core/      shared kernel: AppResult, AppError, dispatchers, Kotlin helpers
domain/    model, repository contracts, use cases — no Android, no network
data/      remote (Retrofit), local (Room), fake, repository implementations
feature/   Compose screens and ViewModels
di/        Hilt modules (the composition root)
```

Dependencies point inwards: `feature` → `domain` ← `data`. Only `di` knows every layer.

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The instrumented tests (including the Room migration test) need a device or emulator:

```
./gradlew :app:connectedDebugAndroidTest
```

Requires JDK 17 (`JAVA_HOME`) and Android SDK 36. `local.properties` is not versioned.
