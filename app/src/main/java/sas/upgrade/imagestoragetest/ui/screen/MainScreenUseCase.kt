package sas.upgrade.imagestoragetest.ui.screen

import android.net.Uri

/**
 * Domain-level business logic for MainScreen.
 * Handles use cases and orchestrates repository operations.
 */
interface MainScreenUseCase {
    /**
     * Loads all images from storage
     */
    fun loadImages(): List<ImageItem>

    /**
     * Saves an image from the given source URI
     */
    fun saveImage(sourceUri: Uri)

    /**
     * Deletes an image by name
     */
    fun deleteImage(imageName: String)

    /**
     * Formats image metadata for display (business logic for presentation)
     * Composes size and date into a single display string
     */
    fun formatImageDisplayInfo(image: ImageItem): String
}
