package io.github.ultrafeed.app4pda.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Query edits remain local; only explicit submission starts the owner's search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    inAppBar: Boolean = false,
) {
    val text = rememberTextFieldState(value)
    val state = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (text.text.toString() != value) text.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.distinctUntilChanged().collect { currentOnChange(it) }
    }
    fun submit(query: String) {
        if (query.isBlank()) return
        currentOnChange(query)
        keyboard?.hide()
        scope.launch {
            state.animateToCollapsed()
            onSearch(query)
        }
    }
    val input: @Composable () -> Unit = {
        SearchInput(text, state, placeholder, ::submit)
    }
    if (inAppBar) AppBarWithSearch(state = state, inputField = input, modifier = modifier)
    else SearchBar(state = state, inputField = input, modifier = modifier.fillMaxWidth())
    ExpandedFullScreenSearchBar(state = state, inputField = input) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    onSearch: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SearchBarDefaults.InputField(
        textFieldState = text,
        searchBarState = state,
        onSearch = onSearch,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            if (state.targetValue == SearchBarValue.Expanded)
                IconButton(onClick = { scope.launch { state.animateToCollapsed() } }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            else
                IconButton(
                    onClick = { onSearch(text.text.toString()) },
                    enabled = text.text.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, stringResource(R.string.search))
                }
        },
        trailingIcon = {
            if (text.text.isNotEmpty())
                IconButton(onClick = { text.setTextAndPlaceCursorAtEnd("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.search_clear))
                }
        },
    )
}
