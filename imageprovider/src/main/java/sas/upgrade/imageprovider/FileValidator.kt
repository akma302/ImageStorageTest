package sas.upgrade.imageprovider

/**
 * Validates file names for security
 */
fun interface FileValidator {
    /**
     * Validates file name against path traversal and other attacks
     * @param name file name to validate
     * @throws IllegalArgumentException if name is invalid
     */
    fun validateFileName(name: String)
}

class FileValidatorImpl : FileValidator {
    override fun validateFileName(name: String) {
        require(name.isNotBlank()) { "File name cannot be blank" }
        require(!name.contains("..")) { "Invalid file name: path traversal detected" }
        require(!name.contains("/")) { "Invalid file name: contains /" }
        require(!name.contains("\\")) { "Invalid file name: contains \\" }
        require(!name.contains("\u0000")) { "Invalid file name: contains null byte" }
        require(name.length <= 255) { "File name too long" }
    }
}
