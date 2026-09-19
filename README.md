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
| 03 | Flow | cold vs hot, operators, StateFlow/SharedFlow, flowOn/buffer | ⬜ |
| 04 | Retrofit + OkHttp | kotlinx.serialization, interceptors, error mapping | ⬜ |
| 05 | Repository + domain | DTO↔domain mappers, Result wrapper, use cases | ⬜ |
| 06 | Hilt | modules, scopes, qualifiers, multibinding | ⬜ |
| 07 | MVVM + Compose state | UiState, unidirectional data flow, side effects | ⬜ |
| 08 | Room | offline-first, single source of truth | ⬜ |
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

## Build

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires JDK 17 (`JAVA_HOME`) and Android SDK 36. `local.properties` is not versioned.
