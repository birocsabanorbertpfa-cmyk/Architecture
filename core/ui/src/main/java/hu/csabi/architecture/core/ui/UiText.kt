package hu.csabi.architecture.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Lesson 07 — text a ViewModel can produce without touching a `Context`.
 *
 * A ViewModel that formats user-facing strings needs resources, and a `Context` in a
 * ViewModel is a leak waiting to happen plus an untestable dependency. The ViewModel emits
 * a *description* of the text instead, and the composable resolves it — which also means
 * locale changes work, because resolution happens at composition time, not when the state
 * was created.
 *
 * The side benefit shows up in lesson 10: asserting `UiText.Resource(R.string.error_network)`
 * is exact, while asserting a formatted English sentence is brittle.
 */
sealed interface UiText {

    /** Already-final text: a repo name, an API message with no translation. */
    data class Raw(val value: String) : UiText

    data class Resource(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText
}

/** For use inside composition: re-resolves if the configuration (locale) changes. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
}

/**
 * For use outside composition — a snackbar shown from a `LaunchedEffect` body, for example.
 * `stringResource` is a composable and cannot be called there, so the caller passes the
 * `Context` it already has from `LocalContext`.
 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
}
