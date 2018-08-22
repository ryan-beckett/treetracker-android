package org.greenstand.android.TreeTracker.camera

import java.io.File

abstract class AlbumStorageDirFactory {
    abstract fun getAlbumStorageDir(albumName: String): File
}
