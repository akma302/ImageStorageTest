package sas.upgrade.imagestoragetest.ui.screen

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sas.upgrade.imageprovider.ImageContract
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageItem(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageList by remember { mutableStateOf(listOf<ImageItem>()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }

    fun loadImages() {
        imageList = queryImages(context)
    }

    LaunchedEffect(Unit) {
        loadImages()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                saveImageToProvider(context, it)
                loadImages()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { pickImageLauncher.launch("image/*") }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            selectedImage?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp),
                    contentScale = ContentScale.Crop
                )
            }

            LazyColumn {
                items(imageList) { image ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedImage =
                                    Uri.withAppendedPath(ImageContract.CONTENT_URI, image.name)
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = image.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = formatSize(image.sizeBytes) + ", " + formatDate(image.lastModified),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                deleteImageFromProvider(context, image.name)
                                loadImages()
                            }
                        }) {
                            Text("🗑")
                        }
                    }
                }
            }
        }
    }
}


private fun queryImages(context: Context): List<ImageItem> {
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

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return String.format("%.1f KB", kb)
}

private fun formatDate(timeMs: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}


private fun saveImageToProvider(context: Context, sourceUri: Uri) {
    val currentTime = System.currentTimeMillis()
    val fileName = getFileName(context, sourceUri) ?: "image_${currentTime}.jpg"

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

private fun Cursor.getLongOrNull(index: Int): Long? = if (index != -1 && !isNull(index)) getLong(index) else null

private fun deleteImageFromProvider(context: Context, name: String) {
    val uri = Uri.withAppendedPath(ImageContract.CONTENT_URI, name)
    context.contentResolver.delete(uri, null, null)
}

private fun getFileName(context: Context, uri: Uri): String? {
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