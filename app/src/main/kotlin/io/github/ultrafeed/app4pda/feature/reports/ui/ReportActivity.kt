package io.github.ultrafeed.app4pda.feature.reports.ui

import android.R as AndroidR
import android.content.ClipData
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.app.App4PdaApplication
import io.github.ultrafeed.app4pda.app.shell.SettingsGate
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.ui.theme.AppTheme
import io.github.ultrafeed.app4pda.feature.reports.data.CachedCrashReport
import io.github.ultrafeed.app4pda.feature.reports.data.CrashCopyResult
import io.github.ultrafeed.app4pda.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

class ReportActivity : ComponentActivity() {
    private val reports
        get() = (application as App4PdaApplication).crashReports

    private var available by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { available = false }

    private fun validate(): CachedCrashReport? {
        val report = reports.cache.read(intent.getStringExtra(REPORT_ID).orEmpty())
        available = report != null
        handler.removeCallbacks(expire)
        report?.let {
            handler.postDelayed(
                expire,
                (it.createdAt + AppPolicy.CACHE_TTL_MILLIS - System.currentTimeMillis())
                    .coerceAtLeast(
                        1,
                    ),
            )
        }
        return report
    }

    override fun onResume() {
        super.onResume()
        (application as App4PdaApplication).onForeground(this)
        validate()
    }

    override fun onDestroy() {
        handler.removeCallbacks(expire)
        super.onDestroy()
    }

    override fun getResources(): Resources {
        val fallback = super.getResources()
        return (application as? App4PdaApplication)?.localizedResources(baseContext, fallback)
            ?: fallback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = validate()
        setContent {
            SettingsGate {
                val settings: SettingsRepository = koinInject()
                val colors by settings.snapshots.collectAsStateWithLifecycle()
                AppTheme(
                    colors.darkTheme,
                    colors.accentColor,
                    colors.animationsEnabled,
                    colors.useSystemAccent,
                ) {
                    if (available)
                        CrashReportScreen(
                            report?.text.orEmpty(),
                            ::finish,
                            {
                                val id = intent.getStringExtra(REPORT_ID).orEmpty()
                                val result = reports.copy(id)
                                if (
                                    result != CrashCopyResult.COPIED ||
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                                )
                                    Toast.makeText(
                                            this,
                                            if (result == CrashCopyResult.COPIED)
                                                R.string.crash_copied
                                            else R.string.crash_copy_failed,
                                            Toast.LENGTH_LONG,
                                        )
                                        .show()
                                validate()
                            },
                            ::shareReport,
                        )
                    else
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                                Text(stringResource(R.string.crash_missing))
                                TextButton(onClick = ::finish) {
                                    Text(stringResource(AndroidR.string.ok))
                                }
                            }
                        }
                }
            }
        }
    }

    private val ioDispatcher: CoroutineDispatcher by inject(named("io"))

    private fun shareReport() {
        val id = validate()?.id ?: return
        lifecycleScope.launch {
            val file =
                withContext(ioDispatcher) { runCatching { reports.cache.export(id) }.getOrNull() }
            if (validate() == null) return@launch
            if (file == null) {
                Toast.makeText(this@ReportActivity, R.string.error_create_file, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }
            val uri =
                FileProvider.getUriForFile(this@ReportActivity, "$packageName.fileprovider", file)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(getString(R.string.crash_clip_label), uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.action_share_file),
                ),
            )
        }
    }

    companion object {
        const val REPORT_ID: String = "crash_report_id"
    }
}

@Composable
fun CrashReportScreen(
    report: String,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            Text(stringResource(R.string.review), style = MaterialTheme.typography.headlineSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onCopy) { Text(stringResource(AndroidR.string.copy)) }
                OutlinedButton(onShare) {
                    Icon(Icons.Default.Share, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_share_file))
                }
                TextButton(onClose) { Text(stringResource(AndroidR.string.cancel)) }
            }
            Card(
                Modifier.weight(1f).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
            ) {
                SelectionContainer {
                    Text(
                        report,
                        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
