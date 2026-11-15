package com.ares.callguard.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class PickedContact(
    val name: String,
    val phoneNumber: String?,
    val photoPath: String?,
)

/**
 * Reads the single contact entry the user picked in the system contact picker. The picker
 * grants access to that one entry only, so ARES never needs permission to read all contacts.
 */
class ContactPicker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun read(pickedPhoneUri: Uri): PickedContact? = withContext(Dispatchers.IO) {
        val columns = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI)
        context.contentResolver.query(pickedPhoneUri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            PickedContact(
                name = cursor.getString(0).orEmpty(),
                phoneNumber = cursor.getString(1),
                photoPath = cursor.getString(2)?.let { copyPhoto(Uri.parse(it)) },
            )
        }
    }

    // The picker's access to the photo ends with this session, so keep our own small copy.
    private fun copyPhoto(photoUri: Uri): String? = runCatching {
        val photo = File(File(context.filesDir, "photos").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(photoUri)?.use { input ->
            photo.outputStream().use(input::copyTo)
        }
        photo.absolutePath
    }.getOrNull()
}
