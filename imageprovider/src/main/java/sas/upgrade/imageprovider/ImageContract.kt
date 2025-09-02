package sas.upgrade.imageprovider

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri

object ImageContract {

    /** Authority для ContentProvider */
    const val AUTHORITY = "sas.upgrade.imageprovider"

    /** Базовый URI */
    val BASE_CONTENT_URI: Uri = "content://$AUTHORITY".toUri()

    /** Путь для доступа к изображениям */
    const val PATH_IMAGES = "images"

    /** Полный URI к коллекции изображений */
    val CONTENT_URI: Uri = BASE_CONTENT_URI.buildUpon().appendPath(PATH_IMAGES).build()

    /** Поля для insert/query */
    object Columns {

        const val NAME = "name"
        const val SIZE = "size"
        const val DATE_MODIFIED = "date_modified"
    }

    /** MIME-типы */
    object MimeTypes {

        /** MIME для списка изображений */
        const val DIR = "${ContentResolver.CURSOR_DIR_BASE_TYPE}/$AUTHORITY.$PATH_IMAGES"

        /** MIME для одного изображения */
        const val ITEM = "${ContentResolver.CURSOR_ITEM_BASE_TYPE}/$AUTHORITY.$PATH_IMAGES"
    }
}