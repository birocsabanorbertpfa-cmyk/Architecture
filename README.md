# Architecture Playground

Lépésről lépésre épülő Android demo projekt. Minden lecke = egy commit.
Téma: GitHub repo kereső app (Compose + Coroutines/Flow + Retrofit + Hilt + Room + tesztek).

## Tanmenet

| # | Lecke | Fő téma | Állapot |
|---|---|---|---|
| 00 | Skeleton | Gradle KTS, version catalog, Compose, wrapper | ✅ |
| 01 | Kotlin mélyvíz | sealed interface, value class, inline/reified, delegates, DSL | ✅ |
| 02 | Coroutines | suspend, structured concurrency, dispatcher, cancellation | ⬜ |
| 03 | Flow | cold/hot, operátorok, StateFlow/SharedFlow, flowOn/buffer | ⬜ |
| 04 | Retrofit + OkHttp | kotlinx.serialization, interceptorok, hibakezelés | ⬜ |
| 05 | Repository + domain | DTO↔Domain mapper, Result wrapper, use case | ⬜ |
| 06 | Hilt | modulok, scope-ok, qualifier, multibinding | ⬜ |
| 07 | MVVM + Compose state | UiState, UDF, viewModelScope, side effect | ⬜ |
| 08 | Room | offline-first, single source of truth | ⬜ |
| 09 | Paging 3 | RemoteMediator, Compose integráció | ⬜ |
| 10 | Unit teszt | MockK, Turbine, runTest/TestDispatcher | ⬜ |
| 11 | UI teszt | Compose test, Hilt test modulok | ⬜ |
| 12 | Multi-module | :core / :feature, build-logic konvenció pluginok | ⬜ |
| 13 | Haladó async | channelFlow, custom operátor, WorkManager, retry | ⬜ |
| 14 | Kotlin optimalizáció | inline/value class költségek, KSP | ⬜ |
| 15 | CI + minőség | GitHub Actions, detekt/ktlint, coverage, R8 | ⬜ |
| 16 | Performance | Compose stability, baseline profile | ⬜ |

## Build

```
./gradlew :app:assembleDebug
```

JDK 17 szükséges (`JAVA_HOME`), Android SDK 36. A `local.properties` nincs verziózva.
