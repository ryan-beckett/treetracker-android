package org.greenstand.android.TreeTracker.fragments


import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast

import org.greenstand.android.TreeTracker.activities.CameraActivity
import org.greenstand.android.TreeTracker.activities.MainActivity
import org.greenstand.android.TreeTracker.application.Permissions
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.database.Tree
import org.greenstand.android.TreeTracker.utilities.ValueHelper

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Date

class UpdateTreeFragment : Fragment(), OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private var mImageView: ImageView? = null
    private var mCurrentPhotoPath: String? = null
    private var mTakenPhotoPath: String? = null
    private var treeIdStr = ""
    private var fragment: Fragment? = null
    private var bundle: Bundle? = null
    private var fragmentTransaction: FragmentTransaction? = null
    private var previousBtn: ImageButton? = null
    private var nextBtn: ImageButton? = null
    private var mSharedPreferences: SharedPreferences? = null
    private var userId: Long = 0
    private var photoCursor: MatrixCursor? = null
    private var initialCursor: Cursor? = null

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

        val v = inflater!!.inflate(R.layout.fragment_update_tree, container, false)

        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        mSharedPreferences = activity.getSharedPreferences(
                "org.greenstand.android", Context.MODE_PRIVATE)

        userId = mSharedPreferences!!.getLong(ValueHelper.MAIN_USER_ID, -1)

        (v.findViewById(R.id.fragment_update_tree) as RelativeLayout).visibility = View.INVISIBLE

        (activity.findViewById(R.id.toolbar_title) as TextView).setText(R.string.update_tree)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        mImageView = v.findViewById(R.id.fragment_update_tree_image) as ImageView


        previousBtn = v.findViewById(R.id.fragment_update_tree_previous) as ImageButton
        nextBtn = v.findViewById(R.id.fragment_update_tree_next) as ImageButton

        previousBtn!!.setOnClickListener(this@UpdateTreeFragment)
        nextBtn!!.setOnClickListener(this@UpdateTreeFragment)

        val db = MainActivity.dbHelper.readableDatabase

        val query = "select distinct tree.*, tree._id as the_tree_id, photo.name, location.* from tree " +
                "left outer join tree_photo on tree._id = tree_id " +
                "left outer join photo on photo_id = photo._id " +
                "left outer join location on location._id = tree.location_id " +
                "where (is_outdated = 'N' or is_outdated is null) and is_missing = 'N'"

        Log.e("query", query)

        initialCursor = db.rawQuery(query, null)
        photoCursor = MatrixCursor(arrayOf("name", "the_tree_id", "lat", "long", "accuracy", "time_created", "time_updated", "time_for_update"))
        val orderedList = ArrayList<Tree?>()

        while (initialCursor!!.moveToNext()) {
            val photoLat = java.lang.Double.parseDouble(initialCursor!!.getString(initialCursor!!.getColumnIndex("lat")))
            val photoLong = java.lang.Double.parseDouble(initialCursor!!.getString(initialCursor!!.getColumnIndex("long")))
            val photoAcc = java.lang.Float.parseFloat(initialCursor!!.getString(initialCursor!!.getColumnIndex("accuracy")))

            val results = floatArrayOf(0f, 0f, 0f)
            if (MainActivity.mCurrentLocation != null) {
                Location.distanceBetween(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude,
                        photoLat, photoLong, results)
            }

            val distance = Math.round(results[0])

            if (MainActivity.mCurrentLocation != null) {
                if (MainActivity.mCurrentLocation!!.hasAccuracy()) {

                    var imagePresent = true

                    if (initialCursor!!.getString(initialCursor!!.getColumnIndex("name")) == null) {
                        imagePresent = false
                    }


                    if (distance < MainActivity.mCurrentLocation!!.accuracy + photoAcc) {

                        orderedList.add(Tree(distance,
                                arrayOf<Any?>(if (!imagePresent) null else initialCursor!!.getString(initialCursor!!.getColumnIndex("name")), initialCursor!!.getString(initialCursor!!.getColumnIndex("the_tree_id")), initialCursor!!.getString(initialCursor!!.getColumnIndex("lat")), initialCursor!!.getString(initialCursor!!.getColumnIndex("long")), initialCursor!!.getString(initialCursor!!.getColumnIndex("accuracy")), initialCursor!!.getString(initialCursor!!.getColumnIndex("time_created")), initialCursor!!.getString(initialCursor!!.getColumnIndex("time_updated")), initialCursor!!.getString(initialCursor!!.getColumnIndex("time_for_update")))))
                    }

                }
            }
        }

        Collections.sort(orderedList)

        val iter = orderedList.iterator()
        while (iter.hasNext()) {
            val tree = iter.next()

            Log.e("tree distance", tree?.distance?.let { Integer.toString(it) })

            photoCursor!!.addRow(tree?.restOfData)

        }

        photoCursor!!.moveToFirst()


        if (photoCursor!!.isLast) {
            nextBtn!!.visibility = View.INVISIBLE
        }

        if (photoCursor!!.isFirst) {
            previousBtn!!.visibility = View.INVISIBLE
        }

        if (photoCursor!!.count <= 0) {
            Toast.makeText(activity, "No trees to update", Toast.LENGTH_SHORT).show()
            activity.supportFragmentManager.popBackStack()
        } else {

            mCurrentPhotoPath = photoCursor!!.getString(photoCursor!!.getColumnIndex("name"))

            if (mCurrentPhotoPath != null) {
                if (mCurrentPhotoPath == "") {
                    mCurrentPhotoPath = null
                }
            }


            val noImage = v.findViewById(R.id.fragment_update_tree_no_image) as TextView
            if (mCurrentPhotoPath != null) {
                noImage.visibility = View.INVISIBLE
                setPic()
            } else {
                noImage.visibility = View.VISIBLE
            }

            MainActivity.mCurrentTreeLocation = Location("treetracker")
            MainActivity.mCurrentTreeLocation!!.latitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("lat")))
            MainActivity.mCurrentTreeLocation!!.longitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("long")))
            MainActivity.mCurrentTreeLocation!!.accuracy = java.lang.Float.parseFloat(photoCursor!!.getString(photoCursor!!.getColumnIndex("accuracy")))

            treeIdStr = photoCursor!!.getString(photoCursor!!.getColumnIndex("the_tree_id"))

            val results = floatArrayOf(0f, 0f, 0f)
            if (MainActivity.mCurrentLocation != null) {
                Location.distanceBetween(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude,
                        MainActivity.mCurrentTreeLocation!!.latitude, MainActivity.mCurrentTreeLocation!!.longitude, results)
            }

            val distanceTxt = v.findViewById(R.id.fragment_update_tree_distance) as TextView
            distanceTxt.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)

            val accuracyTxt = v.findViewById(R.id.fragment_update_tree_gps_accuracy) as TextView
            accuracyTxt.text = Integer.toString(Math.round(MainActivity.mCurrentTreeLocation!!.accuracy)) + " " + resources.getString(R.string.meters)


            val createdTxt = v.findViewById(R.id.fragment_update_tree_created) as TextView
            createdTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).lastIndexOf(":"))

            val updatedTxt = v.findViewById(R.id.fragment_update_tree_last_update) as TextView
            updatedTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).lastIndexOf(":"))


            val statusTxt = v.findViewById(R.id.fragment_update_tree_image_status) as TextView

            var dateForUpdate = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            try {
                dateForUpdate = dateFormat.parse(photoCursor!!.getString(photoCursor!!.getColumnIndex("time_for_update")))

                Log.e("dateForupdate", dateForUpdate.toLocaleString())
            } catch (e: Exception) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }

            Log.e("date", dateForUpdate.toLocaleString())
            if (dateForUpdate.before(Date())) {
                statusTxt.setText(R.string.outdated)
            }
        }


        val yesBtn = v.findViewById(R.id.fragment_update_tree_yes) as Button
        yesBtn.setOnClickListener(this@UpdateTreeFragment)

        mImageView = v.findViewById(R.id.fragment_update_tree_image) as ImageView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            FroyoAlbumDirFactory()
        } else {
            BaseAlbumDirFactory()
        }

        if (photoCursor!!.count > 0) {
            takePicture()
        }

        //
        //		do {
        //			mCurrentPhotoPath = photoCursor.getString(photoCursor.getColumnIndex("name"));
        //
        //			setPic();
        //		} while (photoCursor.moveToNext());

        return v
    }

    override fun onClick(v: View) {


        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

        when (v.id) {
            R.id.fragment_update_tree_previous -> if (!photoCursor!!.isFirst) {
                photoCursor!!.moveToPrevious()
                mCurrentPhotoPath = photoCursor!!.getString(photoCursor!!.getColumnIndex("name"))

                val noImage = activity.findViewById(R.id.fragment_update_tree_no_image) as TextView
                if (mCurrentPhotoPath != null) {
                    setPic()
                    noImage.visibility = View.INVISIBLE
                    mImageView!!.visibility = View.VISIBLE
                    Log.d("ovdje", "2")
                } else {
                    noImage.visibility = View.VISIBLE
                    mImageView!!.visibility = View.INVISIBLE
                }

                treeIdStr = photoCursor!!.getString(photoCursor!!.getColumnIndex("the_tree_id"))


                MainActivity.mCurrentTreeLocation = Location("treetracker")
                MainActivity.mCurrentTreeLocation!!.latitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("lat")))
                MainActivity.mCurrentTreeLocation!!.longitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("long")))
                MainActivity.mCurrentTreeLocation!!.accuracy = java.lang.Float.parseFloat(photoCursor!!.getString(photoCursor!!.getColumnIndex("accuracy")))

                val results = floatArrayOf(0f, 0f, 0f)
                if (MainActivity.mCurrentLocation != null) {
                    Location.distanceBetween(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude,
                            MainActivity.mCurrentTreeLocation!!.latitude, MainActivity.mCurrentTreeLocation!!.longitude, results)
                }

                val distanceTxt = activity.findViewById(R.id.fragment_update_tree_distance) as TextView
                distanceTxt.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)

                val accuracyTxt = activity.findViewById(R.id.fragment_update_tree_gps_accuracy) as TextView
                accuracyTxt.text = Integer.toString(Math.round(MainActivity.mCurrentTreeLocation!!.accuracy)) + " " + resources.getString(R.string.meters)


                val createdTxt = activity.findViewById(R.id.fragment_update_tree_created) as TextView
                createdTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).lastIndexOf(":"))

                val updatedTxt = activity.findViewById(R.id.fragment_update_tree_last_update) as TextView
                updatedTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).lastIndexOf(":"))

                if (!photoCursor!!.isLast) {
                    nextBtn!!.visibility = View.VISIBLE
                }

                if (photoCursor!!.isFirst) {
                    previousBtn!!.visibility = View.INVISIBLE
                }

                val statusTxt = activity.findViewById(R.id.fragment_update_tree_image_status) as TextView

                var dateForUpdate = Date()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                try {
                    dateForUpdate = dateFormat.parse(photoCursor!!.getString(photoCursor!!.getColumnIndex("time_for_update")))

                    Log.e("dateForupdate", dateForUpdate.toLocaleString())
                } catch (e: Exception) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                Log.e("date", dateForUpdate.toLocaleString())
                if (dateForUpdate.before(Date())) {
                    statusTxt.setText(R.string.outdated)
                } else {
                    statusTxt.setText(R.string.ok)
                }

            }
            R.id.fragment_update_tree_next ->

                if (!photoCursor!!.isLast) {
                    photoCursor!!.moveToNext()
                    mCurrentPhotoPath = photoCursor!!.getString(photoCursor!!.getColumnIndex("name"))

                    val noImage = activity.findViewById(R.id.fragment_update_tree_no_image) as TextView

                    if (mCurrentPhotoPath != null) {
                        setPic()
                        noImage.visibility = View.INVISIBLE
                        mImageView!!.visibility = View.VISIBLE
                        Log.d("ovdje", "3")
                    } else {
                        noImage.visibility = View.VISIBLE
                        mImageView!!.visibility = View.INVISIBLE
                    }


                    treeIdStr = photoCursor!!.getString(photoCursor!!.getColumnIndex("the_tree_id"))

                    MainActivity.mCurrentTreeLocation = Location("treetracker")
                    MainActivity.mCurrentTreeLocation!!.latitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("lat")))
                    MainActivity.mCurrentTreeLocation!!.longitude = java.lang.Double.parseDouble(photoCursor!!.getString(photoCursor!!.getColumnIndex("long")))
                    MainActivity.mCurrentTreeLocation!!.accuracy = java.lang.Float.parseFloat(photoCursor!!.getString(photoCursor!!.getColumnIndex("accuracy")))

                    val results = floatArrayOf(0f, 0f, 0f)
                    if (MainActivity.mCurrentLocation != null) {
                        Location.distanceBetween(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude,
                                MainActivity.mCurrentTreeLocation!!.latitude, MainActivity.mCurrentTreeLocation!!.longitude, results)
                    }

                    val distanceTxt = activity.findViewById(R.id.fragment_update_tree_distance) as TextView
                    distanceTxt.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)

                    val accuracyTxt = activity.findViewById(R.id.fragment_update_tree_gps_accuracy) as TextView
                    accuracyTxt.text = Integer.toString(Math.round(MainActivity.mCurrentTreeLocation!!.accuracy)) + " " + resources.getString(R.string.meters)


                    val createdTxt = activity.findViewById(R.id.fragment_update_tree_created) as TextView
                    createdTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_created")).lastIndexOf(":"))

                    val updatedTxt = activity.findViewById(R.id.fragment_update_tree_last_update) as TextView
                    updatedTxt.text = photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).substring(0, photoCursor!!.getString(photoCursor!!.getColumnIndex("time_updated")).lastIndexOf(":"))

                    if (!photoCursor!!.isFirst) {
                        previousBtn!!.visibility = View.VISIBLE
                    }

                    if (photoCursor!!.isLast) {
                        nextBtn!!.visibility = View.INVISIBLE
                    }

                    val statusTxt = activity.findViewById(R.id.fragment_update_tree_image_status) as TextView

                    var dateForUpdate = Date()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    try {
                        dateForUpdate = dateFormat.parse(photoCursor!!.getString(photoCursor!!.getColumnIndex("time_for_update")))

                        Log.e("dateForupdate", dateForUpdate.toLocaleString())
                    } catch (e: Exception) {
                        // TODO Auto-generated catch block
                        e.printStackTrace()
                    }

                    Log.e("date", dateForUpdate.toLocaleString())
                    if (dateForUpdate.before(Date())) {
                        statusTxt.setText(R.string.outdated)
                    } else {
                        statusTxt.setText(R.string.ok)
                    }


                }
            R.id.fragment_update_tree_yes -> {

                val saveAndEdit = mSharedPreferences!!.getBoolean(ValueHelper.SAVE_AND_EDIT, true)

                if (!saveAndEdit) {
                    saveToDb()

                    Toast.makeText(activity, "Tree saved", Toast.LENGTH_SHORT).show()
                    activity.supportFragmentManager.popBackStack()
                } else {
                    fragment = UpdateTreeDetailsFragment()
                    bundle = activity.intent.extras

                    if (bundle == null)
                        bundle = Bundle()

                    Log.e("treeIdStr", treeIdStr)

                    bundle!!.putString(ValueHelper.TREE_ID, treeIdStr)

                    Log.e("TakenPhotoPath", mTakenPhotoPath)

                    val bmOptions = BitmapFactory.Options()
                    bmOptions.inJustDecodeBounds = false
                    var testBmp: Bitmap? = BitmapFactory.decodeFile(mTakenPhotoPath, bmOptions)
                    if (testBmp == null) {
                        testBmp = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions)
                        if (testBmp == null) {
                            Log.e(" i current je null", "1234")
                        }
                    }



                    bundle!!.putString(ValueHelper.TREE_PHOTO, if (testBmp == null) mCurrentPhotoPath else mTakenPhotoPath)

                    fragment!!.arguments = bundle

                    fragmentTransaction = activity.supportFragmentManager
                            .beginTransaction()
                    fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.UPDATE_TREE_DETAILS_FRAGMENT).commit()
                }
            }
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
        val bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions) ?: return

        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(mCurrentPhotoPath)
        } catch (e: IOException) {
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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {

            mTakenPhotoPath = data!!.getStringExtra(ValueHelper.TAKEN_IMAGE_PATH)

            if (mTakenPhotoPath != null) {
                (activity.findViewById(R.id.fragment_update_tree) as RelativeLayout).visibility = View.VISIBLE
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            if ((activity.findViewById(R.id.fragment_update_tree) as RelativeLayout).visibility != View.VISIBLE) {
                activity.supportFragmentManager.popBackStack()
            }
        }
    }

    private fun saveToDb() {
        val dbw = MainActivity.dbHelper.writableDatabase

        var contentValues = ContentValues()

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


        val photoOutdated = "select photo._id from tree_photo left outer join photo on photo_id = photo._id where tree_id = $treeIdStr"

        Log.i("query", photoOutdated)

        val photoCursor = dbw.rawQuery(photoOutdated, null)

        while (photoCursor.moveToNext()) {
            contentValues = ContentValues()
            contentValues.put("is_outdated", "Y")

            dbw.update("photo", contentValues, "_id = ?", arrayOf(photoCursor.getString(photoCursor.getColumnIndex("_id"))))
        }

        //		db.update(TABLE_CONTACTS, values, KEY_ID + " = ?",
        //new String[] { String.valueOf(contact.getID()) });

        // photo
        contentValues = ContentValues()
        contentValues.put("user_id", userId)
        contentValues.put("location_id", locationId)
        contentValues.put("name", mTakenPhotoPath)

        val photoId = dbw.insert("photo", null, contentValues)
        Log.d("photoId", java.lang.Long.toString(photoId))


        val timeToNextUpdate = mSharedPreferences!!.getInt(
                ValueHelper.TIME_TO_NEXT_UPDATE_ADMIN_DB_SETTING, mSharedPreferences!!.getInt(
                ValueHelper.TIME_TO_NEXT_UPDATE_GLOBAL_SETTING,
                ValueHelper.TIME_TO_NEXT_UPDATE_DEFAULT_SETTING))


        val minAccuracy = mSharedPreferences!!.getInt(
                ValueHelper.MIN_ACCURACY_GLOBAL_SETTING,
                ValueHelper.MIN_ACCURACY_DEFAULT_SETTING)

        // settings
        contentValues = ContentValues()
        contentValues.put("time_to_next_update", timeToNextUpdate)
        contentValues.put("min_accuracy", minAccuracy)

        val settingsId = dbw.insert("settings", null, contentValues)
        Log.d("settingsId", java.lang.Long.toString(settingsId))


        // tree
        contentValues = ContentValues()
        contentValues.put("user_id", userId)
        contentValues.put("location_id", locationId)
        contentValues.put("settings_id", settingsId)
        contentValues.put("three_digit_number", "000")
        contentValues.put("is_synced", "N")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        var date = Date()
        val calendar = Calendar.getInstance()
        calendar.time = date


        calendar.add(Calendar.DAY_OF_MONTH, timeToNextUpdate)
        date = calendar.time as Date

        Log.i("date", date.toString())

        contentValues.put("time_for_update", dateFormat.format(date))
        contentValues.put("time_updated", dateFormat.format(Date()))

        dbw.update("tree", contentValues, "_id = ?", arrayOf(treeIdStr))

        val treeId = java.lang.Long.parseLong(treeIdStr)
        Log.d("treeId", java.lang.Long.toString(treeId))

        // tree_photo
        contentValues = ContentValues()
        contentValues.put("tree_id", treeId)
        contentValues.put("photo_id", photoId)

        val treePhotoId = dbw.insert("tree_photo", null, contentValues)
        Log.d("treePhotoId", java.lang.Long.toString(treePhotoId))


        dbw.close()

    }


}//some overrides and settings go here
