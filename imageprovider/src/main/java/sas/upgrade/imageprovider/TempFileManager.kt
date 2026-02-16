package sas.upgrade.imageprovider

import android.os.Handler
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Manages temporary file lifecycle for read operations
 */
fun interface TempFileManager {
    /**
     * Opens temp file for reading with auto-cleanup on close
     * @param tempFile the temporary file containing decrypted data
     * @param cleanupHandler handler for cleanup callback
     * @return ParcelFileDescriptor that deletes temp file when closed
     */
    fun openTempFileWithCleanup(tempFile: File, cleanupHandler: Handler): ParcelFileDescriptor
}

class TempFileManagerImpl : TempFileManager {
    override fun openTempFileWithCleanup(tempFile: File, cleanupHandler: Handler): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(
            tempFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
            cleanupHandler,
            ParcelFileDescriptor.OnCloseListener {
                tempFile.delete()
            }
        )
    }
}
