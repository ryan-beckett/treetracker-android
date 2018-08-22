package org.greenstand.android.TreeTracker.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.Camera
import android.hardware.Camera.PictureCallback
import android.media.ExifInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView

import org.greenstand.android.TreeTracker.BuildConfig
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.camera.AlbumStorageDirFactory
import org.greenstand.android.TreeTracker.camera.BaseAlbumDirFactory
import org.greenstand.android.TreeTracker.camera.CameraPreview
import org.greenstand.android.TreeTracker.camera.FroyoAlbumDirFactory
import org.greenstand.android.TreeTracker.utilities.Utils
import org.greenstand.android.TreeTracker.utilities.ValueHelper

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

import timber.log.Timber


class CameraActivity : Activity(), PictureCallback, OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null
    private val TAG = "Camera activity"
    private val mPicture: PictureCallback? = null
    private var mAlbumStorageDirFactory: AlbumStorageDirFactory? = null
    private var mCurrentPhotoPath: String? = null
    private var mImageView: ImageView? = null
    private val mParameters: View? = null
    private var mCurrentPictureData: ByteArray? = null
    private var cancelImg: ImageButton? = null
    private var captureButton: ImageButton? = null
    private var tmpImageFile: File? = null
    private var openCameraTask: AsyncTask<String, Void, String>? = null
    private var safeToTakePicture = true

    private val albumName: String
        get() = getString(R.string.album_name)

    private val albumDir: File?
        get() {
            var storageDir: File? = null

            if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                storageDir = mAlbumStorageDirFactory!!.getAlbumStorageDir(albumName)
                if (storageDir != null) {
                    if (!storageDir.mkdirs()) {
                        if (!storageDir.exists()) {
                            Log.d("CameraSample", "failed to create directory")
                            return null
                        }
                    }
                }
            } else {
                Log.v(getString(R.string.app_name), "External storage is not mounted READ/WRITE.")
            }
            return storageDir
        }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_preview)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            mAlbumStorageDirFactory = FroyoAlbumDirFactory()
        } else {
            mAlbumStorageDirFactory = BaseAlbumDirFactory()
        }

        mImageView = findViewById(R.id.camera_preview_taken) as ImageView


        cancelImg = findViewById(R.id.camera_preview_cancel) as ImageButton
        captureButton = findViewById(R.id.button_capture) as ImageButton


        // Add a listener to the buttons
        captureButton!!.setOnClickListener(this@CameraActivity)
        cancelImg!!.setOnClickListener(this@CameraActivity)


        openCameraTask = OpenCameraTask().execute(*arrayOf())

    }

    /** Check if this device has a camera  */
    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    override fun onPictureTaken(data: ByteArray, camera: Camera) {
        captureButton!!.visibility = View.INVISIBLE
        cancelImg!!.visibility = View.INVISIBLE

        mCurrentPictureData = data
        tmpImageFile = null
        try {
            tmpImageFile = File.createTempFile("tmpimage.jpg", null, cacheDir)
        } catch (e: IOException) {
            Log.e("file not", "created")
            e.printStackTrace()
        }

        try {
            val fo = FileOutputStream(tmpImageFile!!)
            fo.write(data)
            fo.close()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        setPic()
        safeToTakePicture = true
        savePicture()      //skip picture preview
        releaseCamera()
    }

    @Throws(IOException::class)
    private fun galleryAddPic() {
        val mediaScanIntent = Intent(
                "android.intent.action.MEDIA_SCANNER_SCAN_FILE")

        val photo = Utils.resizedImage(mCurrentPhotoPath!!)

        val bytes = ByteArrayOutputStream()
        photo.compress(Bitmap.CompressFormat.JPEG, 70, bytes)

        val f = File(mCurrentPhotoPath!!)
        try {
            f.createNewFile()
            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            fo.close()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }


        //		File f = new File(mCurrentPhotoPath);
        val contentUri = FileProvider.getUriForFile(this@CameraActivity,
                BuildConfig.APPLICATION_ID + ".provider", createImageFile())
        //Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.data = contentUri
        sendBroadcast(mediaScanIntent)
    }

    override fun onPause() {
        super.onPause()
        releaseCamera()       // release the camera immediately on pause event
    }

    private fun releaseCamera() {
        if (mCamera != null) {
            mCamera!!.release()        // release the camera for other applications
            mCamera = null
            Timber.d("camera released")
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = ValueHelper.JPEG_FILE_PREFIX + timeStamp + "_"
        val albumF = albumDir

        return File.createTempFile(imageFileName, ValueHelper.JPEG_FILE_SUFFIX, albumF)
    }

    @Throws(IOException::class)
    private fun setUpPhotoFile(): File {
        val f = createImageFile()
        mCurrentPhotoPath = f.absolutePath

        return f
    }

    private fun setPic() {

        /* There isn't enough memory to open up more than a couple camera photos */
        /* So pre-scale the target bitmap into which the file is decoded */

        /* Get the size of the image */
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(tmpImageFile!!.absolutePath, bmOptions)
        val imageWidth = bmOptions.outWidth

        // Calculate your sampleSize based on the requiredWidth and
        // originalWidth
        // For e.g you want the width to stay consistent at 500dp
        val requiredWidth = (500 * resources.displayMetrics.density).toInt()

        var sampleSize = Math.ceil((imageWidth.toFloat() / requiredWidth.toFloat()).toDouble()).toInt()

        Log.e("sampleSize ", Integer.toString(sampleSize))
        // If the original image is smaller than required, don't sample
        if (sampleSize < 1) {
            sampleSize = 1
        }

        Log.e("sampleSize 2 ", Integer.toString(sampleSize))
        bmOptions.inSampleSize = sampleSize
        bmOptions.inPurgeable = true
        bmOptions.inPreferredConfig = Bitmap.Config.RGB_565
        bmOptions.inJustDecodeBounds = false

        /* Decode the JPEG file into a Bitmap */
        val bitmap = BitmapFactory.decodeFile(tmpImageFile!!.absolutePath, bmOptions) ?: return


        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(tmpImageFile!!.absolutePath)
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        val orientString = exif!!.getAttribute(ExifInterface.TAG_ORIENTATION)
        val orientation = if (orientString != null)
            Integer.parseInt(orientString)
        else
            ExifInterface.ORIENTATION_NORMAL
        var rotationAngle = 0
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
            rotationAngle = 90
        if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
            rotationAngle = 180
        if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
            rotationAngle = 270

        val matrix = Matrix()
        matrix.setRotate(rotationAngle.toFloat(), bitmap.width.toFloat() / 2,
                bitmap.height.toFloat() / 2)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bmOptions.outWidth, bmOptions.outHeight, matrix, true)

        /* Associate the Bitmap to the ImageView */
        mImageView!!.setImageBitmap(rotatedBitmap)
        mImageView!!.visibility = View.VISIBLE
    }

    fun onOrientationChanged(orientation: Int) {

    }

    override fun onClick(v: View) {
        v.isHapticFeedbackEnabled = true
        // get an image from the camera
        if (safeToTakePicture && mCamera != null) {     //check mCamera isn't null to avoid error
            safeToTakePicture = false
            mCamera!!.takePicture(null, null, this@CameraActivity)
            Log.e("take", "pic")
        }
    }

    private fun savePicture() {
        var pictureFile: File? = null
        try {
            pictureFile = setUpPhotoFile()
            mCurrentPhotoPath = pictureFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            pictureFile = null
            mCurrentPhotoPath = null
        }

        var saved = true
        try {
            val fos = FileOutputStream(pictureFile!!)
            fos.write(mCurrentPictureData!!)
            fos.close()
            galleryAddPic()
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: " + e.message)
            saved = false
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: " + e.message)
            saved = false
        } catch (e: Exception) {
            Log.d(TAG, "Error accessing file: " + e.message)
            saved = false
        }

        if (saved) {
            val data = Intent()
            data.putExtra(ValueHelper.TAKEN_IMAGE_PATH, mCurrentPhotoPath)
            setResult(Activity.RESULT_OK, data)

        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }


    internal inner class OpenCameraTask : AsyncTask<String, Void, String>() {

        override fun onPreExecute() {
            cancelImg!!.visibility = View.INVISIBLE
            captureButton!!.visibility = View.INVISIBLE
        }

        override fun doInBackground(vararg params: String): String? {
            mCamera = cameraInstance
            return null
        }

        override fun onPostExecute(response: String) {
            super.onPostExecute(response)

            if (mCamera == null) {
                openCameraTask = OpenCameraTask().execute(*arrayOf())
            } else {
                // Create our Preview view and set it as the content of our activity.
                mPreview = CameraPreview(this@CameraActivity, mCamera!!)
                val preview = findViewById(R.id.camera_preview) as FrameLayout
                preview.addView(mPreview)
                cancelImg!!.visibility = View.VISIBLE
                captureButton!!.visibility = View.VISIBLE
            }
        }
    }

    companion object {

        val MEDIA_TYPE_IMAGE = 1

        /** A safe way to get an instance of the Camera object.  */
        // attempt to get a Camera instance
        // returns null if camera is unavailable
        val cameraInstance: Camera?
            get() {
                var c: Camera? = null
                try {
                    c = Camera.open()
                } catch (e: Exception) {
                    Log.i("in use", e.localizedMessage)
                }

                return c
            }

        /** Create a file Uri for saving an image or video  */
        private fun getOutputMediaFileUri(type: Int): Uri {
            return Uri.fromFile(getOutputMediaFile(type))
        }

        /** Create a File for saving an image or video  */
        private fun getOutputMediaFile(type: Int): File? {
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

            val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "MyCameraApp")
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                    return null
                }
            }

            // Create a media file name
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val mediaFile: File
            if (type == MEDIA_TYPE_IMAGE) {
                mediaFile = File(mediaStorageDir.path + File.separator +
                        "IMG_" + timeStamp + ".jpg")
            } else {
                return null
            }
            return mediaFile
        }
    }
}



