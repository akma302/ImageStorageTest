package sas.upgrade.imageprovider

import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import sas.upgrade.core.CryptoManager
import java.io.File

/**
 * Handles encryption operations for write mode with background processing
 */
fun interface EncryptionHandler {
    /**
     * Opens temp file for writing, encrypts to target on close (in background thread)
     * @param tempFile temporary file to write plaintext to
     * @param encryptedFile target file for encrypted output
     * @param cryptoManager encryption utility
     * @return ParcelFileDescriptor for writing
     */
    fun openForEncryptedWrite(
        tempFile: File,
        encryptedFile: File,
        cryptoManager: CryptoManager
    ): ParcelFileDescriptor
}

class EncryptionHandlerImpl : EncryptionHandler {

    private val handlerThread = HandlerThread("EncryptionThread").apply { start() }
    private val backgroundHandler = Handler(handlerThread.looper)

    override fun openForEncryptedWrite(
        tempFile: File,
        encryptedFile: File,
        cryptoManager: CryptoManager
    ): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(
            tempFile,
            ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE,
            backgroundHandler,
            ParcelFileDescriptor.OnCloseListener {
                try {
                    cryptoManager.encrypt(tempFile, encryptedFile)
                } finally {
                    tempFile.delete()
                }
            }
        )
    }
}
