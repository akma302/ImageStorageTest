package sas.upgrade.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ImageStorageKey"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // GCM рекомендует 12 байт IV
        private const val TAG_LENGTH = 128 // бит
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(input: File, output: File) {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        // сохраняем IV прямо в начало файла
        FileOutputStream(output).use { fileOut ->
            fileOut.write(cipher.iv)
            CipherOutputStream(fileOut, cipher).use { cos ->
                FileInputStream(input).use { fis ->
                    fis.copyTo(cos)
                }
            }
        }
    }

    fun decrypt(input: File, output: File) {
        FileInputStream(input).use { fileIn ->
            // читаем IV из начала файла
            val iv = ByteArray(IV_SIZE)
            fileIn.read(iv)

            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))

            CipherInputStream(fileIn, cipher).use { cis ->
                FileOutputStream(output).use { fos ->
                    cis.copyTo(fos)
                }
            }
        }
    }

    /**
     * Encrypt raw bytes. IV will be prepended to the output.
     */
    fun encryptBytes(input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encryptedData = cipher.doFinal(input)
        return cipher.iv + encryptedData // prepend IV
    }

    /**
     * Decrypt raw bytes that have IV prepended.
     */
    fun decryptBytes(input: ByteArray): ByteArray {
        require(input.size > IV_SIZE) { "Invalid encrypted data" }

        val iv = input.copyOfRange(0, IV_SIZE)
        val encryptedData = input.copyOfRange(IV_SIZE, input.size)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))

        return cipher.doFinal(encryptedData)
    }

}