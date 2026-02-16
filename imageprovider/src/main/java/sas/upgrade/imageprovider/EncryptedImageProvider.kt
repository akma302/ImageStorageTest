package sas.upgrade.imageprovider

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.BaseColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import androidx.core.net.toUri
import sas.upgrade.core.CryptoManager
import java.io.FileInputStream
import java.io.FileNotFoundException

class EncryptedImageProvider : ContentProvider() {

    companion object {

        private const val IMAGES = 1
        private const val IMAGE_ID = 2

        private const val TABLE = "images"
    }

    private lateinit var db: SQLiteDatabase

    private val cryptoManager = CryptoManager()
    private lateinit var storageDir: File

    // Delegated functional interfaces for improved logic
    private val fileOperations: FileOperations by FileOperationsDelegate()
    private val tempFileManager: TempFileManager by TempFileManagerDelegate()
    private val encryptionHandler: EncryptionHandler by EncryptionHandlerDelegate()
    private val fileValidator: FileValidator by FileValidatorDelegate()

    // Handler for cleanup callbacks (used by tempFileManager)
    private val cleanupHandlerThread by lazy {
        android.os.HandlerThread("TempFileCleanup").apply { start() }
    }
    private val cleanupHandler by lazy { Handler(cleanupHandlerThread.looper) }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(
            ImageContract.AUTHORITY,
            ImageContract.PATH_IMAGES,
            IMAGES
        )
        addURI(
            ImageContract.AUTHORITY,
            "${ImageContract.PATH_IMAGES}/*",
            IMAGE_ID
        )
    }


    override fun onCreate(): Boolean {
        storageDir = File(context?.filesDir, ImageContract.PATH_IMAGES).apply { mkdirs() }
        db = object : SQLiteOpenHelper(context, "images.db", null, 1) {

            override fun onCreate(db: SQLiteDatabase) {

                db.execSQL(
                    """
                    CREATE TABLE $TABLE(
                        ${ImageContract.Columns.NAME} TEXT PRIMARY KEY,
                        ${ImageContract.Columns.SIZE} INTEGER NOT NULL,
                        ${ImageContract.Columns.DATE_MODIFIED} INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
        }.writableDatabase
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uriMatcher.match(uri) != IMAGES) {
            throw IllegalArgumentException("Unknown URI $uri")
        }
        val v = values ?: throw IllegalArgumentException("ContentValues is null")

        val name = v.getAsString(ImageContract.Columns.NAME)
            ?: throw IllegalArgumentException("Missing ${ImageContract.Columns.NAME}")
        val size = v.getAsLong(ImageContract.Columns.SIZE)
            ?: throw IllegalArgumentException("Missing ${ImageContract.Columns.SIZE}")
        val date = v.getAsLong(ImageContract.Columns.DATE_MODIFIED)
            ?: System.currentTimeMillis()

        // базовая валидация имени (path traversal)
        // require(!name.contains("..") && !name.contains("/") && !name.contains("\\")) {
        //     "Invalid file name: $name"
        // }
        fileValidator.validateFileName(name)

        // пишем/обновляем метаданные
        val row = ContentValues().apply {
            put(ImageContract.Columns.NAME, name)
            put(ImageContract.Columns.SIZE, size)
            put(ImageContract.Columns.DATE_MODIFIED, date)
        }
        db.insertWithOnConflict(TABLE, null, row, SQLiteDatabase.CONFLICT_REPLACE)

        // файл создастся при первом openFile("w") (мы не шифруем/не создаём его здесь)
        val result = Uri.withAppendedPath(ImageContract.CONTENT_URI, name)
        context?.contentResolver?.notifyChange(ImageContract.CONTENT_URI, null)
        return result
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {

        if (uriMatcher.match(uri) != IMAGE_ID) {
            throw IllegalArgumentException("Unknown URI $uri")
        }

        val imageName = uri.lastPathSegment
            ?: throw IllegalArgumentException("Missing file name")

        // IMPROVED: throw FileNotFoundException for read mode instead of creating empty file
        val encryptedFile = if (mode.contains("r")) {
            fileOperations.getFileForRead(storageDir, imageName)
        } else {
            File(storageDir, imageName).apply { if (!exists()) createNewFile() }
        }

        // val encryptedFile = File(storageDir, imageName)
        // if (!encryptedFile.exists()) {
        //     encryptedFile.createNewFile()
        //     // throw FileNotFoundException("File not found: $imageName")
        // }

        // Запись в файл
        if (mode.contains("w")) {

            val tempFile = File.createTempFile("enc_", null, context?.cacheDir)
            tempFile.deleteOnExit()

            // IMPROVED: removed unused fileDescriptor, use background thread for encryption
            return encryptionHandler.openForEncryptedWrite(tempFile, encryptedFile, cryptoManager)

            // val fileDescriptor = ParcelFileDescriptor.open(
            //     encryptedFile,
            //     ParcelFileDescriptor.MODE_WRITE_ONLY or
            //             ParcelFileDescriptor.MODE_CREATE or
            //             ParcelFileDescriptor.MODE_TRUNCATE
            // )

            // return ParcelFileDescriptor.open(
            //     tempFile,
            //     ParcelFileDescriptor.MODE_WRITE_ONLY or
            //             ParcelFileDescriptor.MODE_CREATE or
            //             ParcelFileDescriptor.MODE_TRUNCATE,
            //     Handler(Looper.getMainLooper()), // Handler (можно null — listener вызовется в Binder потоке)
            //     ParcelFileDescriptor.OnCloseListener {
            //         try {
            //             // когда клиент закрыл дескриптор -> зашифровать во "взрослый" файл
            //             cryptoManager.encrypt(tempFile, encryptedFile)
            //         } finally {
            //             tempFile.delete()
            //         }
            //     }
            // )
        }

        // Чтение из файла
        if (mode.contains("r")) {
            val fileSize = encryptedFile.length()

            val tempFile = File.createTempFile("dec_", null, context?.cacheDir)
            tempFile.deleteOnExit()

            if (fileSize <= 256 * 1024) {
                // 📍 Memory mode для маленьких файлов
                val encryptedBytes = encryptedFile.readBytes()
                val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes)
                tempFile.writeBytes(decryptedBytes)
            } else {
                // 📍 File mode для больших файлов
                cryptoManager.decrypt(encryptedFile, tempFile)
            }

            // IMPROVED: auto-delete temp file when client closes descriptor
            return tempFileManager.openTempFileWithCleanup(tempFile, cleanupHandler)

            // return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        throw IllegalArgumentException("Unsupported mode: $mode")
    }

    @SuppressLint("Recycle")
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val proj = projection?.takeIf { it.isNotEmpty() }
            ?: arrayOf(
                ImageContract.Columns.NAME,
                ImageContract.Columns.SIZE,
                ImageContract.Columns.DATE_MODIFIED
            )

        return when (uriMatcher.match(uri)) {
            IMAGES -> {
                val c = db.query(
                    TABLE,
                    proj,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    sortOrder ?: "${ImageContract.Columns.DATE_MODIFIED} DESC"
                )
                c.setNotificationUri(
                    requireNotNull(context).contentResolver,
                    ImageContract.CONTENT_URI
                )
                c
            }

            IMAGE_ID -> {
                val name = uri.lastPathSegment ?: return null
                val sel = buildString {
                    append("${ImageContract.Columns.NAME} = ?")
                    if (!selection.isNullOrBlank()) append(" AND ($selection)")
                }
                val args =
                    arrayListOf(name).apply { selectionArgs?.let { addAll(it) } }.toTypedArray()

                db.query(
                    TABLE,
                    proj,
                    sel,
                    args,
                    null,
                    null,
                    sortOrder
                ).apply {
                    setNotificationUri(requireNotNull(context).contentResolver, uri)
                }
            }

            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return when (uriMatcher.match(uri)) {
            IMAGE_ID -> {
                // удаляем конкретный файл + его метаданные
                val name =
                    uri.lastPathSegment ?: throw IllegalArgumentException("Missing file name")

                // 1) удалить файл (если есть)
                File(storageDir, name).let { f -> if (f.exists()) f.delete() }

                // 2) удалить запись
                val count = db.delete(
                    TABLE,
                    "${ImageContract.Columns.NAME} = ?",
                    arrayOf(name)
                )
                if (count > 0) context?.contentResolver?.notifyChange(
                    ImageContract.CONTENT_URI,
                    null
                )
                count
            }

            IMAGES -> {
                // batch delete по selection — сначала найдём имена, чтобы удалить файлы
                val toDelete = mutableListOf<String>()
                db.query(
                    TABLE,
                    arrayOf(ImageContract.Columns.NAME),
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
                ).use { c ->
                    val idx = c.getColumnIndexOrThrow(ImageContract.Columns.NAME)
                    while (c.moveToNext()) toDelete += c.getString(idx)
                }
                // удалить файлы
                toDelete.forEach { n ->
                    File(
                        storageDir,
                        n
                    ).let { f -> if (f.exists()) f.delete() }
                }

                // удалить записи
                val count = db.delete(TABLE, selection, selectionArgs)
                if (count > 0) context?.contentResolver?.notifyChange(
                    ImageContract.CONTENT_URI,
                    null
                )
                count
            }

            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            IMAGES, IMAGE_ID -> "image/*" // для всех изображений возвращаем общий MIME-тип
            else -> null
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
}