package sas.upgrade.imageprovider

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Lazy delegate for FileOperations
 */
class FileOperationsDelegate : ReadOnlyProperty<Any?, FileOperations> {
    private val instance by lazy { FileOperationsImpl() }
    override fun getValue(thisRef: Any?, property: KProperty<*>): FileOperations = instance
}

/**
 * Lazy delegate for TempFileManager
 */
class TempFileManagerDelegate : ReadOnlyProperty<Any?, TempFileManager> {
    private val instance by lazy { TempFileManagerImpl() }
    override fun getValue(thisRef: Any?, property: KProperty<*>): TempFileManager = instance
}

/**
 * Lazy delegate for EncryptionHandler
 */
class EncryptionHandlerDelegate : ReadOnlyProperty<Any?, EncryptionHandler> {
    private val instance by lazy { EncryptionHandlerImpl() }
    override fun getValue(thisRef: Any?, property: KProperty<*>): EncryptionHandler = instance
}

/**
 * Lazy delegate for FileValidator
 */
class FileValidatorDelegate : ReadOnlyProperty<Any?, FileValidator> {
    private val instance by lazy { FileValidatorImpl() }
    override fun getValue(thisRef: Any?, property: KProperty<*>): FileValidator = instance
}
