package io.github.ultrafeed.app4pda.data.googleplay

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import java.io.File

class PlayPackageInstaller(private val packages: PackageInspector) {
    fun install(context: Context, directory: File) {
        val group = checkNotNull(PlayDownloadGroup.read(directory)) { "Delivery manifest missing" }
        group.withInstallApks(context.cacheDir) { files -> install(context, group, files) }
    }

    private fun install(context: Context, group: PlayDownloadGroup, files: List<File>) {
        val baseFile =
            files.firstOrNull {
                packages.artifacts.readApk(it.path, 0)?.packageName == group.packageName
            } ?: error("Base APK missing")
        val base =
            checkNotNull(
                packages.artifacts.readApk(
                    baseFile.path,
                    packages.artifacts.signatureQueryFlags,
                ),
            ) {
                "Invalid base APK"
            }
        require(
            base.packageName == group.packageName &&
                PackageInfoCompat.getLongVersionCode(base) == group.versionCode &&
                !packages.artifacts.getSignatures(base).isNullOrEmpty(),
        ) {
            "Inconsistent APK delivery"
        }
        // A split cannot be parsed as a standalone package. PackageInstaller validates
        // package, version and signatures of the entire session before committing it.
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(group.packageName)
                setSize(files.sumOf { it.length() })
            }
        val id = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(id).use { session ->
                writeApks(session, files)
                val intent = Intent(context, PlayInstallReceiver::class.java)
                val flags =
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE
                        else 0
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
            committed = true
        } finally {
            if (!committed) installer.abandonSession(id)
        }
    }

    private fun writeApks(session: PackageInstaller.Session, files: List<File>) {
        files.forEach { file ->
            session.openWrite(file.name, 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
        }
    }
}

class PlayInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation =
                    IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_INTENT,
                        Intent::class.java,
                    )
                confirmation?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit
            else ->
                Toast.makeText(
                        context,
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                        Toast.LENGTH_LONG,
                    )
                    .show()
        }
    }
}
