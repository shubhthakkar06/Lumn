package com.example.musicapp.data.cloud

import android.content.Context
import java.io.File

/** Hands profile photo changes to cloud sync, so the photo follows the account. */
object ProfilePhotoSync {
    fun photoChanged(context: Context, file: File?) {
        CloudSync.get(context).uploadProfilePhoto(file)
    }
}
