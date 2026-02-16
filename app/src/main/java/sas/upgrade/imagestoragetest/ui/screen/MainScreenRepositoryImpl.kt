package sas.upgrade.imagestoragetest.ui.screen

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import sas.upgrade.imageprovider.ImageContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * Default implementation of MainScreenRepository.
 * Currently delegates to existing private functions (logic will be refactored later).
 */
class MainScreenRepositoryImpl(private val context: Context) : MainScreenRepository {

    override fun loadImages(): List<ImageItem> {
        // TODO: Refactor - currently delegates to existing private function
        return queryImagesInternal()
    }

    override fun saveImage(sourceUri: Uri) {
        // TODO: Refactor - currently delegates to existing private function
        saveImageToProviderInternal(sourceUri)
    }

    override fun deleteImage(imageName: String) {
        // TODO: Refactor - currently delegates to existing private function
        deleteImageFromProviderInternal(imageName)
    }

    override fun formatSize(bytes: Long): String {
        // TODO: Refactor - currently delegates to existing private function
        return formatSizeInternal(bytes)
    }

    override fun formatDate(timeMs: Long): String {
        // TODO: Refactor - currently delegates to existing private function
        return formatDateInternal(timeMs)
    }

    // ============================================
    // Private implementation methods (existing logic preserved)
    // ============================================

    private fun queryImagesInternal(): List<ImageItem> {
        val cursor = context.contentResolver.query(
            ImageContract.CONTENT_URI,
            arrayOf(
                ImageContract.Columns.NAME,
                ImageContract.Columns.SIZE,
                ImageContract.Columns.DATE_MODIFIED
            ),
            null, null, null
        )
        val list = mutableListOf<ImageItem>()
        cursor?.use {
            val nameIdx = it.getColumnIndexOrThrow(ImageContract.Columns.NAME)
            val sizeIdx = it.getColumnIndexOrThrow(ImageContract.Columns.SIZE)
            val dateIdx = it.getColumnIndexOrThrow(ImageContract.Columns.DATE_MODIFIED)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx)
                val size = it.getLong(sizeIdx)
                val date = it.getLong(dateIdx)
                list.add(ImageItem(name, size, date))
            }
        }
        return list
    }

    private fun formatSizeInternal(bytes: Long): String {
        val kb = bytes / 1024.0
        return String.format("%.1f KB", kb)
    }

    private fun formatDateInternal(timeMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }

    private fun saveImageToProviderInternal(sourceUri: Uri) {
        val currentTime = System.currentTimeMillis()
        val fileName = getFileNameInternal(sourceUri) ?: "image_${currentTime}.jpg"

        var size: Long? = null
        // Получаем размер и дату модификации
        val fileStats = context.contentResolver.query(sourceUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                size = cursor.getLongOrNull(cursor.getColumnIndex(OpenableColumns.SIZE))
            }
        }

        val values = ContentValues().apply {
            put(ImageContract.Columns.NAME, fileName)
            size?.let { put(ImageContract.Columns.SIZE, it) }
            put(ImageContract.Columns.DATE_MODIFIED, currentTime)
        }

        // Сохраняем только метаданные через insert
        val metadataUri = context.contentResolver.insert(ImageContract.CONTENT_URI, values)
            ?: throw IllegalArgumentException("Failed to insert metadata")

        // Теперь сохраняем сам файл через openFile (или любой поток, который читает исходное изображение и пишет в Provider)
        context.contentResolver.openOutputStream(metadataUri)?.use { outputStream ->
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    private fun deleteImageFromProviderInternal(name: String) {
        val arrayList = arrayListOf<Int>(1, 2, 3)
        val linkedList = LinkedList(arrayList)

        val uri = Uri.withAppendedPath(ImageContract.CONTENT_URI, name)
        context.contentResolver.delete(uri, null, null)
    }

    private fun getFileNameInternal(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun Cursor.getLongOrNull(index: Int): Long? =
        if (index != -1 && !isNull(index)) getLong(index) else null
}
