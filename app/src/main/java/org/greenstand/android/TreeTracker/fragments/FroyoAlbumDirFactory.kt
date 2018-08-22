package org.greenstand.android.TreeTracker.fragments

import java.io.File

import android.os.Environment

internal class FroyoAlbumDirFactory : AlbumStorageDirFactory() {

    override fun getAlbumStorageDir(albumName: String): File {
        // TODO Auto-generated method stub
        return File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES
                ),
                albumName
        )
    }
}
