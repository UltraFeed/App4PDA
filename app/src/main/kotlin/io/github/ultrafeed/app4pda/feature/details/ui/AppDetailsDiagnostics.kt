package io.github.ultrafeed.app4pda.feature.details.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitch
import io.github.ultrafeed.app4pda.core.ui.components.SectionSwitches
import io.github.ultrafeed.app4pda.core.ui.theme.AppLayout
import io.github.ultrafeed.app4pda.feature.details.AppDetails
import io.github.ultrafeed.app4pda.feature.details.DetailsDebugStep

@Composable
fun AppDetailsDiagnostics(
    data: AppDetails,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_debug)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                SectionSwitches(
                    listOf(R.string.debug_information, R.string.debug_rules, R.string.debug_source)
                        .map {
                            SectionSwitch(
                                stringResource(it),
                            )
                        },
                    tab,
                    modifier =
                        Modifier.padding(
                            horizontal = AppLayout.content,
                            vertical = AppLayout.itemGap,
                        ),
                ) {
                    tab = it
                }
                when (tab) {
                    0 ->
                        SelectionContainer {
                            Text(
                                listOf(data.title, data.packageName, data.version, data.debugReport)
                                    .filter(String::isNotBlank)
                                    .joinToString("\n\n"),
                                Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp),
                            )
                        }

                    1 -> DiagnosticRules(data, selected) { selected = it }

                    2 ->
                        SelectionContainer {
                            Text(
                                data.debugHtml.ifBlank { data.debugSource },
                                Modifier.verticalScroll(rememberScrollState()).padding(top = 16.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ruleTitle(key: String): String =
    stringResource(
        when (key) {
            "app_type" -> R.string.debug_rule_type
            "app_group" -> R.string.debug_rule_group
            "app_name_desc_extra" -> R.string.debug_rule_title
            "app_dt_upd",
            "app_date_update",
            "post_update" -> R.string.debug_rule_date

            "app_scr" -> R.string.debug_rule_gallery
            "app_ver" -> R.string.info_version
            "apk_url" -> R.string.debug_rule_files
            "app_desc_shot" -> R.string.debug_rule_description
            "google_play_url" -> R.string.debug_rule_play
            else -> R.string.debug_rule_content
        },
    )

@Composable
private fun DiagnosticRules(data: AppDetails, selected: Int, onSelect: (Int) -> Unit) {

    val step = data.debugSteps.getOrNull(selected)
    if (step == null) {
        LazyColumn(Modifier) {
            itemsIndexed(data.debugSteps) { index, item ->
                ListItem(
                    content = { Text(ruleTitle(item.title)) },
                    supportingContent = {
                        Text(item.title, fontFamily = FontFamily.Monospace)
                    },
                    trailingContent = {
                        TextButton(onClick = { onSelect(index) }) {
                            Text(stringResource(R.string.action_open))
                        }
                    },
                )
            }
        }
    } else {
        TextButton(onClick = { onSelect(-1) }) {
            Text(stringResource(R.string.debug_rules))
        }
        DiagnosticRuleValue(step)
    }
}

@Composable
private fun DiagnosticRuleValue(
    step: DetailsDebugStep,
) {
    SelectionContainer {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                ruleTitle(step.title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(step.title, fontFamily = FontFamily.Monospace)
            Text(
                stringResource(R.string.debug_pattern),
                Modifier.padding(top = 12.dp),
            )
            Text(
                step.pattern.ifBlank {
                    stringResource(R.string.debug_empty)
                },
                fontFamily = FontFamily.Monospace,
            )
            Text(
                stringResource(R.string.debug_result),
                Modifier.padding(top = 12.dp),
            )
            Text(
                step.result.ifBlank {
                    stringResource(R.string.debug_empty)
                },
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
