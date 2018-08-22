package org.greenstand.android.TreeTracker.fragments

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast

import org.greenstand.android.TreeTracker.activities.CameraActivity
import org.greenstand.android.TreeTracker.activities.MainActivity
import org.greenstand.android.TreeTracker.application.Permissions
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.utilities.ValueHelper

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class UpdateTreeDetailsFragment : Fragment(), OnClickListener, OnCheckedChangeListener, TextWatcher, ActivityCompat.OnRequestPermissionsResultCallback {

    private var mImageView: ImageView? = null
    private var mCurrentPhotoPath: String? = null
    private val fragment: Fragment? = null
    private val bundle: Bundle? = null
    private val fragmentTransaction: FragmentTransaction? = null
    private var userId: Long = 0
    private var mSharedPreferences: SharedPreferences? = null
    private var treeIdStr: String? = null
    private var mTreeIsMissing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        menu!!.clear()
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val v = inflater!!.inflate(R.layout.fragment_update_tree_details, container, false)

        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        (activity.findViewById(R.id.toolbar_title) as TextView).setText(R.string.update_tree)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val extras = arguments

        treeIdStr = extras.getString(ValueHelper.TREE_ID)
        mCurrentPhotoPath = extras.getString(ValueHelper.TREE_PHOTO)

        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = false
        val testBmp = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)

        if (testBmp == null) {
            val db = MainActivity.dbHelper.readableDatabase

            val query = "select * from tree_photo " +
                    "left outer join photo on photo._id = photo_id " +
                    "where is_outdated = 'N' and tree_id = " + treeIdStr

            Log.e("query", query)

            val photoCursor = db.rawQuery(query, null)

            if (photoCursor.count > 0) {
                photoCursor.moveToFirst()
                mCurrentPhotoPath = photoCursor.getString(photoCursor.getColumnIndex("name"))
            }

            db.close()
        }



        mSharedPreferences = activity.getSharedPreferences(
                "org.greenstand.android", Context.MODE_PRIVATE)

        userId = mSharedPreferences!!.getLong(ValueHelper.MAIN_USER_ID, -1)

        val saveBtn = v.findViewById(R.id.fragment_update_tree_details_save) as Button
        saveBtn.setOnClickListener(this@UpdateTreeDetailsFragment)


        val treeMissingChk = v.findViewById(R.id.fragment_update_tree_details_missing_tree) as CheckBox
        treeMissingChk.setOnCheckedChangeListener(this@UpdateTreeDetailsFragment)

        val takePhoto = v.findViewById(R.id.fragment_update_tree_details_take_photo) as ImageButton
        takePhoto.setOnClickListener(this@UpdateTreeDetailsFragment)

        mImageView = v.findViewById(R.id.fragment_update_tree_details_image) as ImageView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            FroyoAlbumDirFactory()
        } else {
            BaseAlbumDirFactory()
        }

        setPic()

        val newTreeDistance = v.findViewById(R.id.fragment_update_tree_details_distance) as TextView
        newTreeDistance.text = Integer.toString(0) + " " + resources.getString(R.string.meters)

        val newTreeGpsAccuracy = v.findViewById(R.id.fragment_update_tree_details_gps_accuracy) as TextView
        if (MainActivity.mCurrentLocation != null) {
            newTreeGpsAccuracy.text = Integer.toString(Math.round(MainActivity.mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
        } else {
            newTreeGpsAccuracy.text = "0 " + resources.getString(R.string.meters)
        }


        val timeToNextUpdate = mSharedPreferences!!.getInt(
                ValueHelper.TIME_TO_NEXT_UPDATE_ADMIN_DB_SETTING, mSharedPreferences!!.getInt(
                ValueHelper.TIME_TO_NEXT_UPDATE_GLOBAL_SETTING,
                ValueHelper.TIME_TO_NEXT_UPDATE_DEFAULT_SETTING))

        val newTreetimeToNextUpdate = v.findViewById(R.id.fragment_update_tree_details_next_update) as EditText
        newTreetimeToNextUpdate.setText(Integer.toString(timeToNextUpdate))

        if (mSharedPreferences!!.getBoolean(ValueHelper.TIME_TO_NEXT_UPDATE_ADMIN_DB_SETTING_PRESENT, false)) {
            newTreetimeToNextUpdate.isEnabled = false
        }

        newTreetimeToNextUpdate.addTextChangedListener(this@UpdateTreeDetailsFragment)

        return v
    }

    override fun onClick(v: View) {

        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

        when (v.id) {

            R.id.fragment_update_tree_details_save ->


                if (mTreeIsMissing) {
                    val builder = AlertDialog.Builder(activity)

                    builder.setTitle(R.string.tree_missing)
                    builder.setMessage(R.string.you_are_about_to_mark_this_tree_as_missing)


                    builder.setPositiveButton(R.string.yes) { dialog, which ->
                        saveToDb()

                        Toast.makeText(activity, "Tree saved", Toast.LENGTH_SHORT)
                                .show()
                        val manager = activity.supportFragmentManager
                        val second = manager.getBackStackEntryAt(1)
                        manager.popBackStack(second.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)

                        dialog.dismiss()
                    }


                    builder.setNegativeButton(R.string.no) { dialog, which ->
                        // Code that is executed when clicking NO

                        dialog.dismiss()
                    }


                    val alert = builder.create()
                    alert.show()
                } else {
                    saveToDb()

                    Toast.makeText(activity, "Tree saved", Toast.LENGTH_SHORT)
                            .show()


                    val manager = activity.supportFragmentManager
                    val second = manager.getBackStackEntryAt(1)
                    manager.popBackStack(second.id, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }

            R.id.fragment_update_tree_details_take_photo -> takePicture()
        }

    }


    private fun takePicture() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA),
                    Permissions.MY_PERMISSION_CAMERA)
        } else {
            val takePictureIntent = Intent(activity, CameraActivity::class.java)
            startActivityForResult(takePictureIntent, ValueHelper.INTENT_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Permissions.MY_PERMISSION_CAMERA && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePicture()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {

            mCurrentPhotoPath = data!!.getStringExtra(ValueHelper.TAKEN_IMAGE_PATH)

            if (mCurrentPhotoPath != null) {
                (activity.findViewById(R.id.fragment_update_tree_details) as RelativeLayout).visibility = View.VISIBLE

                setPic()
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            if ((activity.findViewById(R.id.fragment_update_tree_details) as RelativeLayout).visibility != View.VISIBLE) {
                activity.supportFragmentManager.popBackStack()
            }
        }

    }

    private fun saveToDb() {
        val dbw = MainActivity.dbHelper.writableDatabase

        var contentValues = ContentValues()

        val removePhoto = activity.findViewById(R.id.fragment_update_tree_details_remove_photo) as CheckBox

        // location
        contentValues.put("user_id", userId)

        MainActivity.mCurrentLocation!!.accuracy
        contentValues.put("user_id", userId)
        contentValues.put("accuracy",
                java.lang.Float.toString(MainActivity.mCurrentLocation!!.accuracy))
        contentValues.put("lat",
                java.lang.Double.toString(MainActivity.mCurrentLocation!!.latitude))
        contentValues.put("long",
                java.lang.Double.toString(MainActivity.mCurrentLocation!!.longitude))

        val locationId = dbw.insert("location", null, contentValues)

        Log.d("locationId", java.lang.Long.toString(locationId))


        val photoOutdated = "select photo._id from tree_photo left outer join photo on photo_id = photo._id where tree_id = " + treeIdStr!!

        Log.i("query", photoOutdated)

        val photoCursor = dbw.rawQuery(photoOutdated, null)

        while (photoCursor.moveToNext()) {
            contentValues = ContentValues()
            contentValues.put("is_outdated", "Y")

            dbw.update("photo", contentValues, "_id = ?", arrayOf(photoCursor.getString(photoCursor.getColumnIndex("_id"))))
        }

        var photoId: Long = -1

        if (!removePhoto.isChecked) {
            // photo
            contentValues = ContentValues()
            contentValues.put("user_id", userId)
            contentValues.put("location_id", locationId)
            contentValues.put("name", mCurrentPhotoPath)

            photoId = dbw.insert("photo", null, contentValues)
            Log.d("photoId", java.lang.Long.toString(photoId))
        }


        val newTreetimeToNextUpdate = activity.findViewById(R.id.fragment_update_tree_details_next_update) as EditText
        val timeToNextUpdate = Integer.parseInt(newTreetimeToNextUpdate.text.toString())


        val minAccuracy = mSharedPreferences!!.getInt(
                ValueHelper.MIN_ACCURACY_GLOBAL_SETTING,
                ValueHelper.MIN_ACCURACY_DEFAULT_SETTING)

        // settings
        contentValues = ContentValues()
        contentValues.put("time_to_next_update", timeToNextUpdate)
        contentValues.put("min_accuracy", minAccuracy)

        val settingsId = dbw.insert("settings", null, contentValues)
        Log.d("settingsId", java.lang.Long.toString(settingsId))


        // note
        val content = (activity.findViewById(R.id.fragment_update_tree_details_note) as EditText).text.toString()
        contentValues = ContentValues()
        contentValues.put("user_id", userId)
        contentValues.put("content", content)

        val noteId = dbw.insert("note", null, contentValues)
        Log.d("noteId", java.lang.Long.toString(noteId))


        // tree
        contentValues = ContentValues()
        contentValues.put("location_id", locationId)
        contentValues.put("settings_id", settingsId)
        contentValues.put("is_synced", "N")
        contentValues.put("is_priority", "N")

        if (mTreeIsMissing) {
            contentValues.put("is_missing", "Y")
            contentValues.put("cause_of_death_id", noteId)
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        var date = Date()
        val calendar = Calendar.getInstance()
        calendar.time = date


        calendar.add(Calendar.DAY_OF_MONTH, timeToNextUpdate)
        date = calendar.time as Date

        Log.i("date", date.toString())

        contentValues.put("time_for_update", dateFormat.format(date))
        contentValues.put("time_updated", dateFormat.format(Date()))

        dbw.update("tree", contentValues, "_id = ?", treeIdStr?.let { arrayOf<String>(it) })

        val treeId = java.lang.Long.parseLong(treeIdStr)
        Log.d("treeId", java.lang.Long.toString(treeId))

        if (!removePhoto.isChecked) {
            // tree_photo
            contentValues = ContentValues()
            contentValues.put("tree_id", treeId)
            contentValues.put("photo_id", photoId)

            val treePhotoId = dbw.insert("tree_photo", null, contentValues)
            Log.d("treePhotoId", java.lang.Long.toString(treePhotoId))
        }


        if (!mTreeIsMissing) {
            // tree_note
            contentValues = ContentValues()
            contentValues.put("tree_id", treeId)
            contentValues.put("note_id", noteId)

            val treeNoteId = dbw.insert("tree_note", null, contentValues)
            Log.d("treeNoteId", java.lang.Long.toString(treeNoteId))
        }


        dbw.close()

    }

    private fun setPic() {

        /* Get the size of the image */
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)
        val imageWidth = bmOptions.outWidth
        // Calculate your sampleSize based on the requiredWidth and
        // originalWidth
        // For e.g you want the width to stay consistent at 500dp
        val requiredWidth = (500 * resources.displayMetrics.density).toInt()

        Log.e("required Width ", Integer.toString(requiredWidth))
        Log.e("imageWidth  ", Integer.toString(imageWidth))

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
        val bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)

        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(mCurrentPhotoPath)
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

        Log.d("rotationAngle", Integer.toString(rotationAngle))

        val matrix = Matrix()
        matrix.setRotate(rotationAngle.toFloat(), bitmap.width.toFloat() / 2,
                bitmap.height.toFloat() / 2)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bmOptions.outWidth, bmOptions.outHeight, matrix, true)

        /* Associate the Bitmap to the ImageView */
        mImageView!!.setImageBitmap(rotatedBitmap)
        mImageView!!.visibility = View.VISIBLE
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.fragment_update_tree_details_missing_tree -> {
                mTreeIsMissing = isChecked
                val noteTxt = activity.findViewById(R.id.fragment_update_tree_details_note) as EditText

                if (isChecked) {
                    noteTxt.hint = activity.resources.getString(R.string.cause_of_death)
                } else {
                    noteTxt.hint = activity.resources.getString(R.string.your_note)
                }
            }

            else -> {
            }
        }

    }

    override fun afterTextChanged(s: Editable) {
        Log.e("days", s.toString())

        if (s.toString() != null) {
            try {
                if (Integer.parseInt(s.toString()) > 365) {
                    val newTreetimeToNextUpdate = activity.findViewById(R.id.fragment_update_tree_details_next_update) as EditText
                    newTreetimeToNextUpdate.setText(Integer.toString(365))
                }
            } catch (e: NumberFormatException) {


                e.printStackTrace()
            }

        }


    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int,
                                   after: Int) {
        // TODO Auto-generated method stub

    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // TODO Auto-generated method stub

    }

    companion object {

        fun calculateInSampleSize(options: BitmapFactory.Options,
                                  reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                // Calculate ratios of height and width to requested height and
                // width
                val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
                val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())

                // Choose the smallest ratio as inSampleSize value, this will
                // guarantee
                // a final image with both dimensions larger than or equal to the
                // requested height and width.
                inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
            }

            return inSampleSize
        }
    }

}// some overrides and settings go here
