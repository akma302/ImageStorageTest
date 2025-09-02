package sas.upgrade.imagestoragetest

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import sas.upgrade.imagestoragetest.ui.screen.MainScreen
import sas.upgrade.imagestoragetest.ui.theme.ImageStorageTestTheme
import java.io.IOException

class MainActivity : ComponentActivity() {

    private var lastSavedUri: Uri? = null
    private lateinit var imageView: ImageView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(::saveImageToProvider) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
//        setContentView(R.layout.activity_main)
//
//        imageView = findViewById(R.id.imageView)
//
//        findViewById<Button>(R.id.btnPick).setOnClickListener {
//            pickImageFromGallery()
//        }
//
//        findViewById<Button>(R.id.btnLoad).setOnClickListener {
//            loadImageFromProvider()
//        }

        setContent {
            ImageStorageTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen()
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                }
            }
        }
    }

    private fun pickImageFromGallery() = pickImageLauncher.launch("image/*")

    private fun saveImageToProvider(sourceUri: Uri) {
        try {
            val imageBytes = readBytesFromUri(sourceUri)

            val values = ContentValues().apply {
                put("name", "gallery_image_${System.currentTimeMillis()}")
                put("data", imageBytes)
            }

//            // Для варианта с SQLite
//            val savedUri = contentResolver.insert(
//                Uri.parse("content://com.example.encryptedimagedb/images"),
//                values
//            )

//             Для варианта с файлами:
             val savedUri = contentResolver.insert(
                 "content://sas.upgrade.imageprovider/image".toUri(),
                 values
             )

            savedUri?.let {
                lastSavedUri = it
                Toast.makeText(this, "Изображение сохранено", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageFromProvider() {
        lastSavedUri?.let { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.setImageBitmap(bitmap)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Нет сохранённого изображения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBytesFromUri(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: throw IOException("Не удалось открыть URI")
}
//
//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    ImageStorageTestTheme {
//        Greeting("Android")
//    }
//}