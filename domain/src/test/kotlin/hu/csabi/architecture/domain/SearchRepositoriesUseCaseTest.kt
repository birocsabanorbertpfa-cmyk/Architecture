package hu.csabi.architecture.domain

import com.google.common.truth.Truth.assertThat
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.core.result.AppResult
import hu.csabi.architecture.core.result.errorOrNull
import hu.csabi.architecture.domain.usecase.SearchRepositoriesUseCase
import hu.csabi.architecture.testing.FakeRepoRepository
import hu.csabi.architecture.testing.repo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Lesson 10 — the cheapest, most valuable tests in the project.
 *
 * No Android, no network, no database, no mocking framework: the domain layer was built to
 * be testable like this, and these run in milliseconds. If the business rules only became
 * testable through a ViewModel or an emulator, that would be the architecture's failure, not
 * the test's.
 *
 * `runTest` is still needed because the use case suspends, but nothing here waits on time.
 */
class SearchRepositoriesUseCaseTest {

    private val repository = FakeRepoRepository()
    private val useCase = SearchRepositoriesUseCase(repository)

    @Test
    fun `rejects a query shorter than two characters without touching the repository`() = runTest {
        val result = useCase.refresh("a")

        assertThat(result.errorOrNull()).isInstanceOf(AppError.InvalidInput::class.java)
        // The important half of the assertion: it failed *before* doing any work.
        assertThat(repository.refreshCount).isEqualTo(0)
    }

    @Test
    fun `trims the query before measuring its length`() = runTest {
        assertThat(useCase.refresh("  a  ").errorOrNull())
            .isInstanceOf(AppError.InvalidInput::class.java)

        assertThat(useCase.refresh("  ok  ")).isInstanceOf(AppResult.Success::class.java)
        assertThat(repository.lastQuery?.raw).isEqualTo("ok")
    }

    @Test
    fun `observe emits an empty list for an invalid query instead of failing`() = runTest {
        // A stream has no error channel here by design: an invalid query is simply no results.
        assertThat(useCase.observe("a").first()).isEmpty()
    }

    @Test
    fun `ranks an exact name match first, then by stars`() = runTest {
        repository.seed(
            listOf(
                repo(id = 1, name = "okhttp-extensions", stars = 9_000),
                repo(id = 2, name = "okhttp", stars = 100),
                repo(id = 3, name = "okhttp-logging", stars = 5_000),
            ),
        )

        val names = useCase.observe("okhttp").first().map { it.name }

        // "okhttp" wins despite having the fewest stars, because an exact match outranks
        // popularity. The rest fall back to star order.
        assertThat(names).containsExactly("okhttp", "okhttp-extensions", "okhttp-logging").inOrder()
    }

    @Test
    fun `ranking ignores case when comparing the name`() = runTest {
        repository.seed(listOf(repo(id = 1, name = "Retrofit", stars = 1), repo(id = 2, name = "retrofit-x", stars = 99)))

        val names = useCase.observe("retrofit").first().map { it.name }

        assertThat(names.first()).isEqualTo("Retrofit")
    }

    @Test
    fun `builds the GitHub query with the language qualifier when given one`() = runTest {
        useCase.refresh("compose", language = "Kotlin")

        assertThat(repository.lastQuery?.raw).isEqualTo("compose language:Kotlin")
    }

    @Test
    fun `blank language is dropped rather than producing an empty qualifier`() = runTest {
        useCase.refresh("compose", language = "   ")

        assertThat(repository.lastQuery?.raw).isEqualTo("compose")
    }

    @Test
    fun `force is passed through, so a manual retry can bypass the cache`() = runTest {
        useCase.refresh("compose", force = true)

        assertThat(repository.lastForce).isTrue()
    }
}
