package org.greenstand.android.TreeTracker.fragments


import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.InflateException
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import org.greenstand.android.TreeTracker.activities.MainActivity
import org.greenstand.android.TreeTracker.application.Permissions
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.utilities.ValueHelper
import org.greenstand.android.TreeTracker.BuildConfig

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date


class MapsFragment : Fragment(), OnClickListener, OnMarkerClickListener, OnMapReadyCallback {

    internal var mSettingCallback: LocationDialogListener? = null

    private val redPulsatingMarkers = ArrayList<Marker>()
    private val redToGreenPulsatingMarkers = ArrayList<Marker>()

    private var mSharedPreferences: SharedPreferences? = null
    private var mCurrentRedToGreenMarkerColor = -1
    private var paused = false
    protected var mCurrentMarkerColor: Int = 0

    private val handler = object : Handler() {

        override fun handleMessage(msg: Message) {

        }
    }

    private var fragment: Fragment? = null

    private var bundle: Bundle? = null

    private var fragmentTransaction: FragmentTransaction? = null

    @get:JvmName("getView_")
    private var view: View? = null

    interface LocationDialogListener {
        fun refreshMap()
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        try {
            mSettingCallback = context as LocationDialogListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(context!!.toString() + " must implement LocationDialogListener")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    override fun onPause() {
        super.onPause()

        paused = true
        Log.d("GPS_Bugs", "MasFragment onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("GPS_Bugs", "MasFragment on Destroy")
    }

    override fun onResume() {
        super.onResume()
        Log.d("GPS_Bugs", "MasFragment onResume")
        if (paused) {
            (childFragmentManager
                    .findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)
        }
        paused = false

        mCurrentRedToGreenMarkerColor = R.drawable.green_pin
        mCurrentMarkerColor = R.drawable.red_pin_pulsating_4

        handler.post(object : Runnable {
            override fun run() {
                if (mCurrentRedToGreenMarkerColor == R.drawable.red_pin) {
                    mCurrentRedToGreenMarkerColor = R.drawable.green_pin
                } else {
                    mCurrentRedToGreenMarkerColor = R.drawable.red_pin
                }
                for (marker in redToGreenPulsatingMarkers) {
                    marker.setIcon(BitmapDescriptorFactory.fromResource(mCurrentRedToGreenMarkerColor))
                }

                if (!paused)
                    handler.postDelayed(this, 500)
            }
        })

        handler.post(object : Runnable {
            override fun run() {
                if (mCurrentMarkerColor == R.drawable.red_pin) {
                    mCurrentMarkerColor = R.drawable.red_pin_pulsating_1
                } else if (mCurrentMarkerColor == R.drawable.red_pin_pulsating_1) {
                    mCurrentMarkerColor = R.drawable.red_pin_pulsating_2
                } else if (mCurrentMarkerColor == R.drawable.red_pin_pulsating_2) {
                    mCurrentMarkerColor = R.drawable.red_pin_pulsating_3
                } else if (mCurrentMarkerColor == R.drawable.red_pin_pulsating_3) {
                    mCurrentMarkerColor = R.drawable.red_pin_pulsating_4
                } else if (mCurrentMarkerColor == R.drawable.red_pin_pulsating_4) {
                    mCurrentMarkerColor = R.drawable.red_pin
                }

                for (marker in redPulsatingMarkers) {
                    marker.setIcon(BitmapDescriptorFactory.fromResource(mCurrentMarkerColor))
                }

                if (!paused)
                    handler.postDelayed(this, 200)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        try {
            val fragment = activity
                    .supportFragmentManager.findFragmentById(
                    R.id.map) as SupportMapFragment
            if (fragment != null)
                activity.supportFragmentManager.beginTransaction().remove(fragment).commit()

        } catch (e: IllegalStateException) {
            //handle this situation because you are necessary will get
            //an exception here :-(
        }

    }


    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        if (view != null) {
            val parent = view!!.parent as ViewGroup
            parent?.removeView(view)
        }
        try {
            view = inflater!!.inflate(R.layout.fragment_map, container, false)
        } catch (e: InflateException) {
            /* map is already there, just return view as it is */
        }

        val v = view

        mSharedPreferences = activity.getSharedPreferences(
                "org.greenstand.android", Context.MODE_PRIVATE)

        if (!(activity as AppCompatActivity).supportActionBar!!.isShowing) {
            Log.d("MainActivity", "toolbar hide")
            (activity as AppCompatActivity).supportActionBar!!.show()
        }
        (activity.findViewById(R.id.toolbar_title) as TextView).setText(R.string.map)
        (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)

        val fab = v!!.findViewById(R.id.fab) as FloatingActionButton
        fab.setOnClickListener(this)

        (childFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)


        val mapGpsAccuracy = v.findViewById(R.id.fragment_map_gps_accuracy) as TextView
        val mapGpsAccuracyValue = v.findViewById(R.id.fragment_map_gps_accuracy_value) as TextView

        val minAccuracy = mSharedPreferences!!.getInt(ValueHelper.MIN_ACCURACY_GLOBAL_SETTING, ValueHelper.MIN_ACCURACY_DEFAULT_SETTING)

        if (mapGpsAccuracy != null) {
            Log.i("ođe", "0")
            if (MainActivity.mCurrentLocation != null) {
                Log.i("ođe", "1")
                if (MainActivity.mCurrentLocation!!.hasAccuracy() && MainActivity.mCurrentLocation!!.accuracy < minAccuracy) {
                    Log.i("ođe", "2")
                    mapGpsAccuracy.setTextColor(Color.GREEN)
                    mapGpsAccuracyValue.setTextColor(Color.GREEN)
                    mapGpsAccuracyValue.text = Integer.toString(Math.round(MainActivity.mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
                    MainActivity.mAllowNewTreeOrUpdate = true
                } else {
                    Log.i("ođe", "3")
                    mapGpsAccuracy.setTextColor(Color.RED)
                    MainActivity.mAllowNewTreeOrUpdate = false

                    if (MainActivity.mCurrentLocation!!.hasAccuracy()) {
                        mapGpsAccuracyValue.setTextColor(Color.RED)
                        mapGpsAccuracyValue.text = Integer.toString(Math.round(MainActivity.mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
                    } else {
                        mapGpsAccuracyValue.setTextColor(Color.RED)
                        mapGpsAccuracyValue.text = "N/A"
                    }
                }
            } else {
                Log.i("ođe", "5")
                if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                            Permissions.MY_PERMISSION_ACCESS_COURSE_LOCATION)
                }
                mapGpsAccuracy.setTextColor(Color.RED)
                mapGpsAccuracyValue.setTextColor(Color.RED)
                mapGpsAccuracyValue.text = "N/A"
                MainActivity.mAllowNewTreeOrUpdate = false
            }

        }

        return v
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Permissions.MY_PERMISSION_ACCESS_COURSE_LOCATION) {
            mSettingCallback?.refreshMap()
        }
    }


    override fun onClick(v: View) {


        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

        val photoCursor: Cursor
        when (v.id) {
            R.id.fab -> {
                Log.d(TAG, "fab click")
                if (MainActivity.mAllowNewTreeOrUpdate || BuildConfig.GPS_ACCURACY == "off") {
                    fragment = NewTreeFragment()
                    bundle = activity.intent.extras
                    fragment!!.arguments = bundle

                    fragmentTransaction = activity.supportFragmentManager
                            .beginTransaction()
                    fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.NEW_TREE_FRAGMENT).commit()
                } else {
                    Toast.makeText(activity, "Insufficient GPS accuracy.", Toast.LENGTH_SHORT).show()
                }
            }
        }//			case R.id.fragment_map_update_tree:
        //
        //				if (MainActivity.mAllowNewTreeOrUpdate) {
        //					SQLiteDatabase db = MainActivity.dbHelper.getReadableDatabase();
        //
        ////					String query = "select * from tree_photo " +
        ////							"left outer join tree on tree._id = tree_id " +
        ////							"left outer join photo on photo._id = photo_id " +
        ////							"left outer join location on location._id = photo.location_id " +
        ////							"where is_outdated = 'N'";
        //
        //					String query = "select * from tree " +
        //							"left outer join location on location._id = tree.location_id " +
        //							"left outer join tree_photo on tree._id = tree_id " +
        //							"left outer join photo on photo._id = photo_id ";
        //
        //					Log.e("query", query);
        //
        //					photoCursor = db.rawQuery(query, null);
        //
        //					if (photoCursor.getCount() <= 0) {
        //						Toast.makeText(getActivity(), "No trees to update", Toast.LENGTH_SHORT).show();
        //						db.close();
        //						return;
        //					}
        //
        //					db.close();
        //
        //					fragment = new UpdateTreeFragment();
        //					bundle = getActivity().getIntent().getExtras();
        //					fragment.setArguments(bundle);
        //
        //					fragmentTransaction = getActivity().getSupportFragmentManager()
        //							.beginTransaction();
        //					fragmentTransaction.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.UPDATE_TREE_FRAGMENT).commit();
        //				} else {
        //					Toast.makeText(getActivity(), "Insufficient GPS accuracy.", Toast.LENGTH_SHORT).show();
        //				}
        //
        //				break;


    }

    override fun onMarkerClick(marker: Marker): Boolean {
        fragment = TreePreviewFragment()
        bundle = activity.intent.extras

        if (bundle == null)
            bundle = Bundle()

        bundle!!.putString(ValueHelper.TREE_ID, marker.title)
        fragment!!.arguments = bundle

        fragmentTransaction = activity.supportFragmentManager
                .beginTransaction()
        fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.TREE_PREVIEW_FRAGMENT).commit()
        return true
    }


    override fun onMapReady(map: GoogleMap) {


        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        map.isMyLocationEnabled = true

        val db = MainActivity.dbHelper.readableDatabase

        val treeCursor = db.rawQuery("select *, tree._id as tree_id from tree left outer join location on location_id = location._id where is_missing = 'N'", null)
        treeCursor.moveToFirst()

        redToGreenPulsatingMarkers.clear()
        redPulsatingMarkers.clear()

        if (treeCursor.count > 0) {

            var latLng = LatLng(-33.867, 151.206)
            val bla = 0

            do {


                Log.e("time_for_update", treeCursor.getString(treeCursor.getColumnIndex("_id")))

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")


                val isSynced = java.lang.Boolean.parseBoolean(treeCursor.getString(treeCursor.getColumnIndex("is_synced")))

                Log.e("issynced", java.lang.Boolean.toString(isSynced))

                var dateForUpdate = Date()
                try {
                    dateForUpdate = dateFormat.parse(treeCursor.getString(treeCursor.getColumnIndex("time_for_update")))
                } catch (e: ParseException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                var updated = Date()
                try {
                    updated = dateFormat.parse(treeCursor.getString(treeCursor.getColumnIndex("time_updated")))
                } catch (e: ParseException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                var created = Date()
                try {
                    created = dateFormat.parse(treeCursor.getString(treeCursor.getColumnIndex("time_created")))
                } catch (e: ParseException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                val priority = treeCursor.getString(treeCursor.getColumnIndex("is_priority")) == "Y"
                latLng = LatLng(java.lang.Double.parseDouble(treeCursor.getString(treeCursor.getColumnIndex("lat"))),
                        java.lang.Double.parseDouble(treeCursor.getString(treeCursor.getColumnIndex("long"))))


                val markerOptions = MarkerOptions()
                        .title(java.lang.Long.toString(treeCursor.getLong(treeCursor.getColumnIndex("tree_id"))))// set Id instead of title
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.green_pin))
                        .position(latLng)
                val marker = map.addMarker(markerOptions)



                if (priority) {
                    redPulsatingMarkers.add(marker)
                    continue
                }


                Log.i("updated", "*************")
                Log.i("updated", updated.toLocaleString())
                Log.i("dateForUpdate", dateForUpdate.toLocaleString())


                if (dateForUpdate.before(Date())) {


                    Log.e("updated", "should be red")

                    val calendar = Calendar.getInstance()
                    calendar.time = dateForUpdate

                    val currCalendar = Calendar.getInstance()
                    currCalendar.time = Date()

                    marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.red_pin))
                }

                //		        Log.i("updated", "*************");
                //		        if (created.before(updated) && !isSynced) {
                //		        	redToGreenPulsatingMarkers.add(marker);
                //		        }
            } while (treeCursor.moveToNext())


            db.close()

            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 20f))

        } else {
            if (MainActivity.mCurrentLocation != null) {
                val myLatLng = LatLng(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 10f))
            }
        }

        map.setOnMarkerClickListener(this@MapsFragment)

        // Other supported types include: MAP_TYPE_NORMAL,
        // MAP_TYPE_TERRAIN, MAP_TYPE_HYBRID and MAP_TYPE_NONE
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
    }

    companion object {
        private val TAG = "MapsFragment"
    }
}//some overrides and settings go here
