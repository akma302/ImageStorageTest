package sas.upgrade.imagestoragetest.ui.screen

import android.net.Uri

/**
 * Implementation of domain-level business logic.
 * Orchestrates repository operations and applies business rules.
 */
class MainScreenUseCaseImpl(
    private val repository: MainScreenRepository
) : MainScreenUseCase {

    override fun loadImages(): List<ImageItem> {
        return repository.loadImages()
    }

    override fun saveImage(sourceUri: Uri) {
        repository.saveImage(sourceUri)
    }

    override fun deleteImage(imageName: String) {
        repository.deleteImage(imageName)
    }

    override fun formatImageDisplayInfo(image: ImageItem): String {
        // Business logic: compose size and date into display format
        val sizeText = repository.formatSize(image.sizeBytes)
        val dateText = repository.formatDate(image.lastModified)
        return "$sizeText, $dateText"
    }
}
