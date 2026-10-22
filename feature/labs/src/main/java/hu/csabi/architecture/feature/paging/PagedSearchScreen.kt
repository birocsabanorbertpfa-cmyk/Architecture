package hu.csabi.architecture.feature.paging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import hu.csabi.architecture.core.error.AppError
import hu.csabi.architecture.domain.model.Repo

/**
 * Lesson 09 — rendering a paged list, including the states nobody remembers to handle.
 */
@Composable
fun PagedSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: PagedSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()

    // Lifecycle-aware by default: collection pauses when the screen is not started.
    val repos = viewModel.repos.collectAsLazyPagingItems()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Paged GitHub search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Room PagingSource + RemoteMediator · ${repos.itemCount} loaded",
            style = MaterialTheme.typography.bodySmall,
        )

        /**
         * Three load states, three different meanings, and conflating them is the usual
         * mistake:
         *  - `refresh`  the first page, or a retry of the whole list
         *  - `append`   the next page, while the user keeps scrolling
         *  - `mediator` the network's state, distinct from the local source's
         */
        when (val refresh = repos.loadState.refresh) {
            is LoadState.Loading -> FullScreenProgress()

            is LoadState.Error -> FullScreenError(
                message = refresh.error.readableMessage(),
                onRetry = repos::retry,
            )

            is LoadState.NotLoading -> if (repos.itemCount == 0) {
                Hint("No repository matched")
            } else {
                RepoPagedList(repos = repos)
            }
        }
    }
}

@Composable
private fun RepoPagedList(
    repos: androidx.paging.compose.LazyPagingItems<Repo>,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        /**
         * `itemKey` keeps item identity stable while pages load, so Compose reuses the right
         * nodes instead of rebuilding rows as the list grows.
         */
        items(count = repos.itemCount, key = repos.itemKey { it.id.value }) { index ->
            // Placeholders are disabled in the PagingConfig, so an item is only null in the
            // brief window before its page is bound. Skipping it is correct, not defensive.
            repos[index]?.let { repo ->
                RepoRow(repo)
                HorizontalDivider()
            }
        }

        // The append state belongs *inside* the list: a spinner at the bottom, not over it.
        when (val append = repos.loadState.append) {
            is LoadState.Loading -> item { InlineProgress() }

            is LoadState.Error -> item {
                InlineError(message = append.error.readableMessage(), onRetry = repos::retry)
            }

            is LoadState.NotLoading -> if (append.endOfPaginationReached) {
                item { Hint("End of results") }
            }
        }
    }
}

/**
 * The typed error from lesson 04 survives the whole way into Paging's `LoadState.Error`,
 * because `AppError` is a `Throwable`. The UI can therefore still tell a rate limit from a
 * dropped connection — something a generic "loading failed" would have thrown away.
 */
private fun Throwable.readableMessage(): String = when (this) {
    is AppError.Network -> "No connection"
    is AppError.RateLimited -> "GitHub rate limit reached"
    is AppError.Http -> "Server error (${code})"
    is AppError.Serialization -> "Unexpected response"
    is AppError.InvalidInput -> reason
    else -> message ?: "Something went wrong"
}

@Composable
private fun RepoRow(repo: Repo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
            repo.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text("★ ${repo.stars.formatted()}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FullScreenProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InlineProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FullScreenError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
