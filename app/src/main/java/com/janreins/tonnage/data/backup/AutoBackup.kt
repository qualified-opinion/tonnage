package com.janreins.tonnage.data.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import java.time.LocalDate

/** The folder can't be reached: deleted, or the permission to write there was lost. */
class FolderUnavailableException : Exception("the folder is no longer available. Pick it again.")

/** The few file operations an automatic backup needs, so the rules can be tested without a phone. */
interface BackupFolder {
    fun names(): List<String>
    /** Creates [name] (it must not exist yet) and writes [bytes] to it. */
    fun create(name: String, bytes: ByteArray)
    /** Writes [bytes] over [name], creating it if needed. */
    fun overwrite(name: String, bytes: ByteArray)
    fun delete(name: String)
    /** Renames [from] to [to]; false when the folder can't rename files. */
    fun rename(from: String, to: String): Boolean
}

/**
 * After each saved workout, writes the same file as a manual backup to the folder chosen in
 * Settings as `tonnage-auto-YYYY-MM-DD.json`, keeping the newest [KEEP] of those files.
 */
object AutoBackup {
    const val KEEP = 7
    private val ownName = Regex("""tonnage-auto-\d{4}-\d{2}-\d{2}\.json""")

    // Outlives the workout screen; finishing a workout never waits for it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })
    private val lock = Mutex()

    fun fileName(date: LocalDate): String = "tonnage-auto-$date.json"

    /** Old automatic backups to delete; any other file in the folder is never touched. */
    fun toDelete(names: List<String>): List<String> =
        names.filter { ownName.matches(it) }.sortedDescending().drop(KEEP)

    /** Starts a backup if a folder is chosen. */
    fun afterWorkoutSaved(context: Context) {
        val app = context.applicationContext
        val settings = SettingsStore.get(app)
        val folder = settings.autoBackupFolder.value ?: return
        scope.launch {
            run(settings, DocumentTreeFolder(app.contentResolver, Uri.parse(folder)), LocalDate.now()) {
                BackupRepository(AppDatabase.getInstance(app)).exportFile(settings)
            }
        }
    }

    /** One automatic backup; records the result in [settings]. */
    internal suspend fun run(
        settings: SettingsStore,
        folder: BackupFolder,
        today: LocalDate,
        export: suspend () -> Pair<BackupContents, ByteArray>,
    ) = lock.withLock {
        try {
            val (contents, bytes) = export()
            write(folder, fileName(today), bytes)
            toDelete(folder.names()).forEach { runCatching { folder.delete(it) } }
            settings.setLastBackupAt(contents.exportedAt)
            settings.setAutoBackupError(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupTooLargeException) {
            settings.setAutoBackupError(e.message)
        } catch (e: FolderUnavailableException) {
            settings.setAutoBackupError(e.message)
        } catch (e: SecurityException) {
            settings.setAutoBackupError(FolderUnavailableException().message)
        } catch (e: FileNotFoundException) {
            settings.setAutoBackupError(FolderUnavailableException().message)
        } catch (e: Exception) {
            settings.setAutoBackupError("the file couldn't be saved (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    /** Writes a temporary file first, so the day's file is only replaced by a complete one. */
    internal fun write(folder: BackupFolder, name: String, bytes: ByteArray) {
        val temp = "$name.tmp"
        if (temp in folder.names()) folder.delete(temp)
        try {
            folder.create(temp, bytes)
        } catch (e: Exception) {
            runCatching { folder.delete(temp) }
            throw e
        }
        if (name in folder.names()) folder.delete(name)
        if (!folder.rename(temp, name)) {
            folder.overwrite(name, bytes)
            runCatching { folder.delete(temp) }
        }
    }

    /** Remembers the picked folder across restarts, releasing the previous one. */
    fun choose(context: Context, treeUri: Uri) {
        val settings = SettingsStore.get(context)
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.autoBackupFolder.value?.takeIf { it != treeUri.toString() }?.let { release(resolver, it) }
        settings.setAutoBackupFolder(treeUri.toString())
    }

    fun turnOff(context: Context) {
        val settings = SettingsStore.get(context)
        settings.autoBackupFolder.value?.let { release(context.contentResolver, it) }
        settings.setAutoBackupFolder(null)
    }

    private fun release(resolver: ContentResolver, treeUri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** The chosen folder's name for Settings, or null if it can't be read. */
    fun folderName(context: Context, treeUri: String): String? = runCatching {
        val tree = Uri.parse(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}

/** A folder picked with the system folder picker. */
private class DocumentTreeFolder(private val resolver: ContentResolver, private val tree: Uri) : BackupFolder {
    private val folderId by lazy { runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrElse { throw FolderUnavailableException() } }
    private val folderUri by lazy { DocumentsContract.buildDocumentUriUsingTree(tree, folderId) }

    private fun children(): Map<String, Uri> {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isWritePermission }) throw FolderUnavailableException()
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = runCatching { resolver.query(query, columns, null, null, null) }.getOrNull()
            ?: throw FolderUnavailableException()
        return cursor.use {
            buildMap {
                while (it.moveToNext()) put(it.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)))
            }
        }
    }

    private fun uriOf(name: String): Uri? = children()[name]

    override fun names(): List<String> = children().keys.toList()

    override fun create(name: String, bytes: ByteArray) {
        // A neutral type, so the provider doesn't add its own extension to the name.
        val uri = DocumentsContract.createDocument(resolver, folderUri, "application/octet-stream", name)
            ?: throw FolderUnavailableException()
        write(uri, bytes)
    }

    override fun overwrite(name: String, bytes: ByteArray) {
        val uri = uriOf(name) ?: DocumentsContract.createDocument(resolver, folderUri, "application/json", name)
            ?: throw FolderUnavailableException()
        write(uri, bytes)
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw FileNotFoundException("Couldn't open the file.")
    }

    override fun delete(name: String) {
        uriOf(name)?.let { DocumentsContract.deleteDocument(resolver, it) }
    }

    override fun rename(from: String, to: String): Boolean {
        val uri = uriOf(from) ?: return false
        return runCatching { DocumentsContract.renameDocument(resolver, uri, to) }.getOrNull() != null &&
            to in names()
    }
}
