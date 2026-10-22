package hu.csabi.architecture.testing

import hu.csabi.architecture.core.coroutines.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Lesson 10 — the payoff of lesson 02's `AppDispatchers` interface.
 *
 * All three dispatchers point at the **same** test dispatcher, which means every
 * `withContext(dispatchers.io)` in production code runs on the test's virtual clock instead
 * of a real thread pool. That is what makes these tests deterministic rather than
 * "deterministic unless the machine is busy".
 *
 * Had `Dispatchers.IO` been called directly in the repository, none of this would be
 * possible — the test would have to sleep and hope.
 */
class TestAppDispatchers(dispatcher: TestDispatcher) : AppDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
