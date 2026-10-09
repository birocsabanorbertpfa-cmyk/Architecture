package hu.csabi.architecture.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Lesson 10 — the rule every ViewModel test needs.
 *
 * `viewModelScope` is hardwired to `Dispatchers.Main`, and on the JVM there is no main
 * looper, so any test touching a ViewModel fails with "Module with the Main dispatcher had
 * failed to initialize". `Dispatchers.setMain` installs a test dispatcher instead.
 *
 * `StandardTestDispatcher` rather than `Unconfined`: it queues coroutines instead of running
 * them eagerly, so the test controls exactly when work happens with `advanceTimeBy` and
 * `runCurrent`. `UnconfinedTestDispatcher` is the shortcut that makes a test pass without
 * expressing any ordering — convenient, and the reason flaky suites exist.
 *
 * `resetMain` in `finished` matters: a leaked main dispatcher leaks into the next test class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
