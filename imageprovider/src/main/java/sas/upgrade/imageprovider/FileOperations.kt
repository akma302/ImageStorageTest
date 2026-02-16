package sas.upgrade.imageprovider

import java.io.File
import java.io.FileNotFoundException

/**
 * Handles file existence checks and creation logic
 */
fun interface FileOperations {
    /**
     * Gets file for reading, throws if not exists
     * @param storageDir base storage directory
     * @param fileName name of the file
     * @return File if exists
     * @throws FileNotFoundException if file doesn't exist
     */
    fun getFileForRead(storageDir: File, fileName: String): File
}

class FileOperationsImpl : FileOperations {
    override fun getFileForRead(storageDir: File, fileName: String): File {
        val file = File(storageDir, fileName)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $fileName")
        }
        return file
    }
}
