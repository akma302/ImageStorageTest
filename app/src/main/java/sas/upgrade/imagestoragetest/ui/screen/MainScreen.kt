package sas.upgrade.imagestoragetest.ui.screen

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
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

data class ImageItem(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

@Composable
fun MainScreen(
    // Inject use case for testability and separation of concerns
    useCase: MainScreenUseCase = MainScreenUseCaseImpl(
        repository = MainScreenRepositoryImpl(LocalContext.current)
    )
) {
    val scope = rememberCoroutineScope()

    var imageList by remember { mutableStateOf(listOf<ImageItem>()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }

    fun loadImages() {
        // Delegate to use case for loading images
        imageList = useCase.loadImages()
    }

    LaunchedEffect(Unit) {
        loadImages()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                // Delegate to use case for saving image
                useCase.saveImage(it)
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
                                // Delegate to use case for business logic
                                text = useCase.formatImageDisplayInfo(image),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                // Delegate to use case for deleting image
                                useCase.deleteImage(image.name)
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