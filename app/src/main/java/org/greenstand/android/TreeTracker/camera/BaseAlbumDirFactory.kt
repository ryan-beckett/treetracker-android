package org.greenstand.android.TreeTracker.camera

import java.io.File

import android.os.Environment

class BaseAlbumDirFactory : AlbumStorageDirFactory() {

    override fun getAlbumStorageDir(albumName: String): File {
        return File(
                Environment.getExternalStorageDirectory().toString()
                        + CAMERA_DIR
                        + albumName
        )
    }

    companion object {

        // Standard storage location for digital camera files
        private val CAMERA_DIR = "/dcim/"
    }
}
