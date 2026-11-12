package io.github.ultrafeed.app4pda.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.navigation.OnScreenResume
import io.github.ultrafeed.app4pda.app.shell.ScreenFrame
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.core.images.AppImageSource
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.core.ui.components.AppErrorCard
import io.github.ultrafeed.app4pda.core.ui.components.AppImage
import io.github.ultrafeed.app4pda.core.ui.components.ConfirmActionDialog
import io.github.ultrafeed.app4pda.core.ui.components.SwitchChoiceItem
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.forumAccounts
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class ForumAccountRow(
    val id: Int,
    val name: String,
    val availableAt: Long,
    val avatarUrl: String?,
    val email: String?,
)

data class ForumAccountsState(
    val accounts: List<ForumAccountRow> = emptyList(),
    val busy: Boolean = false,
    val error: Boolean = false,
    val profileProblem: SourceProblem? = null,
    val selectedId: Int = 0,
    val autoSwitch: Boolean = true,
)

class ForumAccountsViewModel(
    private val settings: AppSettings,
    private val profiles: ForumAuthRepository,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow(false)
    private val profileProblem = MutableStateFlow<SourceProblem?>(null)
    private var refresh: Job? = null

    fun refreshProfiles() {
        if (refresh?.isActive == true) return
        refresh = viewModelScope.launch { profileProblem.value = profiles.refreshProfiles() }
    }

    fun cancelProfiles() {
        refresh?.cancel()
    }

    val state =
        combine(
                settings.changes.map { values ->
                    Triple(
                        values.forumAccounts().map {
                            ForumAccountRow(
                                it.id,
                                it.name,
                                it.searchAvailableAt,
                                it.avatarUrl,
                                it.email,
                            )
                        },
                        values.session.userId,
                        values.autoSwitchForumAccount,
                    )
                },
                busy,
                error,
                profileProblem,
            ) { accounts, saving, failed, profileFailure ->
                ForumAccountsState(
                    accounts.first,
                    saving,
                    failed,
                    profileFailure,
                    accounts.second,
                    accounts.third,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(
                    AppPolicy.SUBSCRIPTION_STOP_TIMEOUT_MILLIS,
                ),
                ForumAccountsState(),
            )

    fun remove(id: Int) = update { settings.accounts.remove(id) }

    fun select(id: Int) = update { settings.accounts.select(id) }

    fun autoSwitch(enabled: Boolean) = update { settings.accounts.setAutoSwitch(enabled) }

    private fun update(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = false
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = true
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
internal fun ForumAccountsScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    val model: ForumAccountsViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    OnScreenResume(model::refreshProfiles)
    DisposableEffect(model) {
        onDispose { model.cancelProfiles() }
    }
    var removing by rememberSaveable { mutableStateOf<Int?>(null) }
    removing?.let { id ->
        ConfirmActionDialog(
            title = stringResource(R.string.menu_delete),
            text = stringResource(R.string.remove_forum_account_confirmation),
            onConfirm = {
                removing = null
                model.remove(id)
            },
            onDismiss = { removing = null },
        )
    }
    ScreenFrame(onBack, stringResource(R.string.forum_accounts)) {
        Column {
            SwitchChoiceItem(
                stringResource(R.string.forum_auto_switch),
                state.autoSwitch,
                model::autoSwitch,
                enabled = !state.busy,
            )
            Text(
                stringResource(R.string.forum_accounts_summary),
                Modifier.padding(AppLayout.content),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onAdd,
                enabled = !state.busy,
                modifier = Modifier.padding(horizontal = AppLayout.content),
            ) {
                Text(stringResource(R.string.forum_account_add))
            }
            state.profileProblem?.let {
                AppErrorCard(
                    it,
                    modifier = Modifier.padding(AppLayout.content),
                    onRetry = model::refreshProfiles,
                )
            }
            if (state.error)
                Text(
                    stringResource(R.string.action_failed),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            AccountList(state, { removing = it }, model::select, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ForumAccountItem(
    account: ForumAccountRow,
    enabled: Boolean,
    index: Int,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    SegmentedListItem(
        onClick = onSelect,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        content = { Text(account.name.ifBlank { account.id.toString() }) },
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppLayout.itemGap),
            ) {
                RadioButton(selected, onClick = null, enabled = enabled)
                AppImage(
                    account.avatarUrl?.let {
                        AppImageSource.Url(
                            it,
                            ImageOrigin.FORUM,
                        )
                    },
                    account.avatarUrl,
                    account.name,
                    Modifier.size(48.dp),
                )
            }
        },
        supportingContent = {
            Column {
                Text(
                    account.email ?: stringResource(R.string.account_email_unavailable),
                )
                Text(
                    if (account.availableAt > System.currentTimeMillis())
                        stringResource(
                            R.string.forum_account_cooldown,
                            DisplayFormats.dateTime(
                                account.availableAt,
                                "dd.MM.yyyy, HH:mm",
                            ),
                        )
                    else stringResource(R.string.forum_account_ready),
                )
            }
        },
        trailingContent = {
            TextButton(onRemove, enabled = enabled) {
                Text(stringResource(R.string.menu_delete))
            }
        },
        modifier =
            Modifier.fillMaxWidth().semantics {
                role = Role.RadioButton
                this.selected = selected
            },
    )
}

@Composable
private fun AccountList(
    state: ForumAccountsState,
    onRemove: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.selectableGroup(),
        contentPadding = AppLayout.listPadding,
        verticalArrangement =
            Arrangement.spacedBy(
                ListItemDefaults.SegmentedGap,
            ),
    ) {
        if (state.accounts.isEmpty())
            item {
                Text(
                    stringResource(R.string.accounts_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        itemsIndexed(state.accounts, key = { _, account -> account.id }) { index, account ->
            ForumAccountItem(
                account,
                !state.busy,
                index,
                state.accounts.size,
                account.id == state.selectedId,
                { onSelect(account.id) },
            ) {
                onRemove(account.id)
            }
        }
    }
}
