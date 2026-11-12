package io.github.ultrafeed.app4pda.feature.installed.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.AppSearchField

@Composable
fun HomeToolbar(query: String, onQuery: (String) -> Unit, onSearch: (String) -> Unit) {
    AppSearchField(
        value = query,
        onValueChange = onQuery,
        onSearch = onSearch,
        placeholder = stringResource(R.string.search),
        inAppBar = true,
    )
}
