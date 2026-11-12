package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.AppSearchField
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout

@Composable
fun CatalogSearchField(value: String, onChange: (String) -> Unit, onSearch: (String) -> Unit) {
    AppSearchField(
        value = value,
        onValueChange = onChange,
        onSearch = { query ->
            onChange(query)
            onSearch(query)
        },
        placeholder = stringResource(R.string.search),
        modifier = Modifier.padding(horizontal = AppLayout.content),
    )
}
