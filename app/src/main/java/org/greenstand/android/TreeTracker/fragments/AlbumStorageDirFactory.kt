package org.greenstand.android.TreeTracker.fragments

import java.io.File

internal abstract class AlbumStorageDirFactory {
    abstract fun getAlbumStorageDir(albumName: String): File
}
