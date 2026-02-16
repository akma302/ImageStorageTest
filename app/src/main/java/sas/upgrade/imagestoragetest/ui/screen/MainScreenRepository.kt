package sas.upgrade.imagestoragetest.ui.screen

import android.net.Uri

/**
 * Repository interface for managing image operations.
 * Separates business logic from UI layer.
 */
interface MainScreenRepository {
    /**
     * Queries all images from the encrypted image provider
     */
    fun loadImages(): List<ImageItem>

    /**
     * Saves an image from the given source URI to the encrypted provider
     */
    fun saveImage(sourceUri: Uri)

    /**
     * Deletes an image by name from the encrypted provider
     */
    fun deleteImage(imageName: String)

    /**
     * Formats file size in bytes to a human-readable string
     */
    fun formatSize(bytes: Long): String

    /**
     * Formats timestamp to a human-readable date string
     */
    fun formatDate(timeMs: Long): String
}
