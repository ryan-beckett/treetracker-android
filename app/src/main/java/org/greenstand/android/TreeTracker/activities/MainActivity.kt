package org.greenstand.android.TreeTracker.activities


import android.app.AlertDialog
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast

import org.apache.http.HttpStatus
import org.greenstand.android.TreeTracker.R
import org.greenstand.android.TreeTracker.api.Api
import org.greenstand.android.TreeTracker.fragments.SettingsFragment
import org.greenstand.android.TreeTracker.managers.DataManager
import org.greenstand.android.TreeTracker.api.models.responses.UserTree
import org.greenstand.android.TreeTracker.application.Permissions
import org.greenstand.android.TreeTracker.database.DatabaseManager
import org.greenstand.android.TreeTracker.database.DbHelper
import org.greenstand.android.TreeTracker.fragments.AboutFragment
import org.greenstand.android.TreeTracker.fragments.DataFragment
import org.greenstand.android.TreeTracker.fragments.LoginFragment
import org.greenstand.android.TreeTracker.fragments.MapsFragment
import org.greenstand.android.TreeTracker.utilities.ValueHelper
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException

class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback, MapsFragment.LocationDialogListener {

    var map: Map<String, String>? = null

    private var mSharedPreferences: SharedPreferences? = null

    private var fragment: Fragment? = null

    private var fragmentTransaction: FragmentTransaction? = null

    private var mDataManager: DataManager<*>? = null
    private var mDatabaseManager: DatabaseManager? = null
    var userTrees: List<UserTree>? = null
        private set

    private var locationManager: LocationManager? = null
    private var mLocationListener: android.location.LocationListener? = null
    private var mLocationUpdatesStarted: Boolean = false

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in onSaveInstanceState(Bundle). **Note: Otherwise it is null.**
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e("on", "create")

        // Application Setup
        val sharedPreferences = getSharedPreferences(ValueHelper.NAME_SPACE, Context.MODE_PRIVATE)
        val token = sharedPreferences.getString(ValueHelper.TOKEN, null)
        Api.instance().setAuthToken(token!!)

        /*
        if(Api.instance().isLoggedIn()){
            getMyTrees();
        }
        */

        mSharedPreferences = this.getSharedPreferences(
                "org.greenstand.android", Context.MODE_PRIVATE)


        dbHelper = DbHelper(this, "databasev2", null, 1)
        mDatabaseManager = DatabaseManager.getInstance(MainActivity.dbHelper)

        try {
            dbHelper.createDataBase()
        } catch (e: IOException) {
            Log.e("nije mi ", "uspjelo")
        }


        if (mSharedPreferences!!.getBoolean(ValueHelper.FIRST_RUN, true)) {

            if (mSharedPreferences!!.getBoolean(ValueHelper.TREE_TRACKER_SETTINGS_USED, true)) {
                mSharedPreferences!!.edit().putBoolean(ValueHelper.TREE_TRACKER_SETTINGS_USED, true).commit()
            }

            mSharedPreferences!!.edit().putBoolean(ValueHelper.FIRST_RUN, false).commit()
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.title = ""

        if (!Api.instance().isLoggedIn) {

            val loginFragment = LoginFragment()
            loginFragment.arguments = intent.extras

            val fragmentTransaction = supportFragmentManager
                    .beginTransaction()
            fragmentTransaction.add(R.id.container_fragment, loginFragment, ValueHelper.LOGIN_FRAGMENT)

            fragmentTransaction.commit()
        } else {
            val extras = intent.extras
            var startDataSync = false
            if (extras != null) {
                if (extras.getBoolean(ValueHelper.RUN_FROM_NOTIFICATION_SYNC)) {
                    startDataSync = true
                }
            }

            if (startDataSync) {
                Log.d("MainActivity", "startDataSync is true")
                val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                mNotificationManager.cancel(ValueHelper.WIFI_NOTIFICATION_ID)

                val dataFragment = DataFragment()
                dataFragment.arguments = intent.extras

                val fragmentTransaction = supportFragmentManager
                        .beginTransaction()
                fragmentTransaction.add(R.id.container_fragment, dataFragment, ValueHelper.DATA_FRAGMENT)

                fragmentTransaction.commit()

            } else if (mSharedPreferences!!.getBoolean(ValueHelper.TREES_TO_BE_DOWNLOADED_FIRST, false)) {
                Log.d("MainActivity", "TREES_TO_BE_DOWNLOADED_FIRST is true")
                var bundle = intent.extras

                fragment = MapsFragment()
                fragment!!.arguments = bundle

                fragmentTransaction = supportFragmentManager
                        .beginTransaction()
                fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.MAP_FRAGMENT).commit()

                if (bundle == null)
                    bundle = Bundle()

                bundle.putBoolean(ValueHelper.RUN_FROM_HOME_ON_LOGIN, true)


                fragment = DataFragment()
                fragment!!.arguments = bundle

                fragmentTransaction = supportFragmentManager
                        .beginTransaction()
                fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.DATA_FRAGMENT).commit()

            } else {
                Log.d("MainActivity", "startDataSync is false")
                val homeFragment = MapsFragment()
                homeFragment.arguments = intent.extras

                val fragmentTransaction = supportFragmentManager
                        .beginTransaction()
                fragmentTransaction.replace(R.id.container_fragment, homeFragment).addToBackStack(ValueHelper.MAP_FRAGMENT)
                fragmentTransaction.commit()
            }

        }


    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        Log.d("MainActivity", "menu_main created")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val bundle: Bundle?
        val fm = supportFragmentManager
        when (item.itemId) {
            android.R.id.home -> {
                Log.d("MainActivity", "press back button")

                Log.d("MainActivity", "click back, back stack count: " + fm.backStackEntryCount)
                for (entry in 0 until fm.backStackEntryCount) {
                    Log.d("MainActivity", "Found fragment: " + fm.getBackStackEntryAt(entry).name)
                }
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                }
                return true
            }
            R.id.action_data -> {
                fragment = DataFragment()
                bundle = intent.extras
                fragment!!.arguments = bundle

                fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.DATA_FRAGMENT).commit()
                for (entry in 0 until fm.backStackEntryCount) {
                    Log.d("MainActivity", "Found fragment: " + fm.getBackStackEntryAt(entry).name)
                }
                return true
            }
            R.id.action_settings -> {
                fragment = SettingsFragment()
                bundle = intent.extras
                fragment!!.arguments = bundle

                fragmentTransaction = supportFragmentManager.beginTransaction()
                fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.SETTINGS_FRAGMENT).commit()
                for (entry in 0 until fm.backStackEntryCount) {
                    Log.d("MainActivity", "Found fragment: " + fm.getBackStackEntryAt(entry).name)
                }
                return true
            }
            R.id.action_about -> {
                val someFragment = supportFragmentManager.findFragmentById(R.id.container_fragment)

                var aboutIsRunning = false

                if (someFragment != null) {
                    if (someFragment is AboutFragment) {
                        aboutIsRunning = true
                    }
                }

                if (!aboutIsRunning) {
                    fragment = AboutFragment()
                    fragment!!.arguments = intent.extras

                    fragmentTransaction = supportFragmentManager
                            .beginTransaction()
                    fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.ABOUT_FRAGMENT).commit()
                }
                for (entry in 0 until fm.backStackEntryCount) {
                    Log.d("MainActivity", "Found fragment: " + fm.getBackStackEntryAt(entry).name)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        Log.i(TAG, "onActivityResult")
        super.onActivityResult(requestCode, resultCode, data)
    }

    /*
     * Called when the Activity is no longer visible at all.
     * Stop updates and disconnect.
     */
    public override fun onRestart() {
        Log.i(TAG, "onRestart")
        super.onRestart()
    }

    /*
     * Called when the Activity is no longer visible at all.
     * Stop updates and disconnect.
     */
    public override fun onStop() {
        Log.i(TAG, "onStop")
        super.onStop()
    }

    public override fun onDestroy() {
        Log.e(TAG, "onDestroy")
        super.onDestroy()
    }


    /*
     * Called when the Activity is going into the background.
     * Parts of the UI may be visible, but the Activity is inactive.
     */
    public override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()

        stopPeriodicUpdates()
    }

    /*
     * Called when the Activity is restarted, even before it becomes visible.
     */
    public override fun onStart() {
        Log.d(TAG, "onStart")
        super.onStart()
    }

    /*
     * Called when the system detects that this Activity is now visible.
     */
    public override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()

        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val builder = AlertDialog.Builder(this@MainActivity)

            builder.setTitle(R.string.enable_location_access)
            builder.setMessage(R.string.you_must_enable_location_access_in_your_settings_in_order_to_continue)

            builder.setPositiveButton(R.string.ok) { dialog, which ->
                if (Build.VERSION.SDK_INT >= 19) {
                    //LOCATION_MODE
                    //Sollution for problem 25 added the ability to pop up location start activity
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } else {
                    //LOCATION_PROVIDERS_ALLOWED

                    val locationProviders = Settings.Secure.getString(contentResolver, Settings.Secure.LOCATION_PROVIDERS_ALLOWED)
                    if (locationProviders == null || locationProviders == "") {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }


                dialog.dismiss()
            }


            builder.setNegativeButton(R.string.cancel) { dialog, which ->
                finish()

                dialog.dismiss()
            }


            val alert = builder.create()
            alert.setCancelable(false)
            alert.setCanceledOnTouchOutside(false)
            alert.show()

        }

        //solution for #57 git
        dbHelper = DbHelper(this, "database", null, 1)
        mDatabaseManager = DatabaseManager.getInstance(MainActivity.dbHelper)

        try {
            dbHelper.createDataBase()
        } catch (e: IOException) {
            Log.e("nije mi ", "uspjelo")
        }

        //end of solution for #57 git

        startPeriodicUpdates()

        if (mSharedPreferences!!.getBoolean(ValueHelper.TREES_TO_BE_DOWNLOADED_FIRST, false)) {

            var bundle = intent.extras

            fragment = MapsFragment()
            fragment!!.arguments = bundle

            fragmentTransaction = supportFragmentManager
                    .beginTransaction()
            fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.MAP_FRAGMENT).commit()

            if (bundle == null)
                bundle = Bundle()

            bundle.putBoolean(ValueHelper.RUN_FROM_HOME_ON_LOGIN, true)


            fragment = DataFragment()
            fragment!!.arguments = bundle

            fragmentTransaction = supportFragmentManager
                    .beginTransaction()
            fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.DATA_FRAGMENT).commit()

        }

    }


    fun onLocationChanged(location: Location) {
        //Log.d("onLocationChanged", location.toString());

        // In the UI, set the latitude and longitude to the value received
        mCurrentLocation = location

        //int minAccuracy = mSharedPreferences.getInt(ValueHelper.MIN_ACCURACY_GLOBAL_SETTING, 0);
        val minAccuracy = 10

        val mapGpsAccuracy = findViewById(R.id.fragment_map_gps_accuracy) as TextView
        val mapGpsAccuracyValue = findViewById(R.id.fragment_map_gps_accuracy_value) as TextView


        if (mapGpsAccuracy != null) {
            if (mCurrentLocation != null) {
                if (mCurrentLocation!!.hasAccuracy() && mCurrentLocation!!.accuracy < minAccuracy) {
                    mapGpsAccuracy.setTextColor(Color.GREEN)
                    mapGpsAccuracyValue.setTextColor(Color.GREEN)
                    mapGpsAccuracyValue.text = Integer.toString(Math.round(mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
                    MainActivity.mAllowNewTreeOrUpdate = true
                } else {
                    mapGpsAccuracy.setTextColor(Color.RED)
                    MainActivity.mAllowNewTreeOrUpdate = false

                    if (mCurrentLocation!!.hasAccuracy()) {
                        mapGpsAccuracyValue.setTextColor(Color.RED)
                        mapGpsAccuracyValue.text = Integer.toString(Math.round(mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
                    } else {
                        mapGpsAccuracyValue.setTextColor(Color.RED)
                        mapGpsAccuracyValue.text = "N/A"
                    }
                }

                if (mCurrentLocation!!.hasAccuracy()) {
                    val newTreeGpsAccuracy = findViewById(R.id.fragment_new_tree_gps_accuracy) as TextView

                    if (newTreeGpsAccuracy != null) {
                        newTreeGpsAccuracy.text = Integer.toString(Math.round(mCurrentLocation!!.accuracy)) + " " + resources.getString(R.string.meters)
                    }
                }
            } else {
                mapGpsAccuracy.setTextColor(Color.RED)
                mapGpsAccuracyValue.setTextColor(Color.RED)
                mapGpsAccuracyValue.text = "N/A"
                MainActivity.mAllowNewTreeOrUpdate = false
            }


            if (mCurrentTreeLocation != null && MainActivity.mCurrentLocation != null) {
                val results = floatArrayOf(0f, 0f, 0f)
                Location.distanceBetween(MainActivity.mCurrentLocation!!.latitude, MainActivity.mCurrentLocation!!.longitude,
                        MainActivity.mCurrentTreeLocation!!.latitude, MainActivity.mCurrentTreeLocation!!.longitude, results)

                val newTreeDistance = findViewById(R.id.fragment_new_tree_distance) as TextView
                if (newTreeDistance != null) {
                    newTreeDistance.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)
                }

                val treePreviewDistance = findViewById(R.id.fragment_tree_preview_distance) as TextView
                if (treePreviewDistance != null) {
                    treePreviewDistance.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)
                }

                val updateTreeDistance = findViewById(R.id.fragment_update_tree_distance) as TextView
                if (updateTreeDistance != null) {
                    updateTreeDistance.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)
                }

                val updateTreeDetailsDistance = findViewById(R.id.fragment_update_tree_details_distance) as TextView
                if (updateTreeDetailsDistance != null) {
                    updateTreeDetailsDistance.text = Integer.toString(Math.round(results[0])) + " " + resources.getString(R.string.meters)
                }
            }
        }
    }


    /*
        } else {
            Log.e("MainActivity", "onSignupResult: failed to signup " + String.valueOf(httpResponseCode) );
            switch (httpResponseCode) {
                case -1:
                    Toast.makeText(MainActivity.this, "Please check your internet connection and try again.", Toast.LENGTH_SHORT).show();
                    break;

                case HttpStatus.SC_CONFLICT:
                    Log.e("conflict", "alert should display");
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    builder.setTitle(R.string.user_already_exists);
                    builder.setMessage(R.string.user_with_that_email);

                    builder.setPositiveButton(R.string.login, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            fragment = new LoginFragment();
                            fragment.setArguments(getIntent().getExtras());

                            fragmentTransaction = getSupportFragmentManager()
                                    .beginTransaction();
                            fragmentTransaction.replace(R.id.container_fragment, fragment).commit();

                            dialog.dismiss();
                        }

                    });


                    builder.setNegativeButton(R.string.reset_password, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {

                            fragment = new ForgotPasswordFragment();
                            fragment.setArguments(getIntent().getExtras());

                            fragmentTransaction = getSupportFragmentManager()
                                    .beginTransaction();
                            fragmentTransaction.replace(R.id.container_fragment, fragment)
                                    .addToBackStack(ValueHelper.FORGOT_PASSWORD_FRAGMENT).commit();


                            dialog.dismiss();
                        }

                    });


                    AlertDialog alert = builder.create();
                    alert.show();
                    break;


                default:
                    break;
            }
        }
    }
*/

    fun transitionToMapsFragment() {
        fragment = MapsFragment()
        fragment!!.arguments = intent.extras
        fragmentTransaction = supportFragmentManager
                .beginTransaction()
        fragmentTransaction!!.replace(R.id.container_fragment, fragment).commit()


        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    Permissions.NECESSARY_PERMISSIONS)
        } else {
            startPeriodicUpdates()
            // getMyTrees();

        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.size > 0) {
            if (requestCode == Permissions.NECESSARY_PERMISSIONS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPeriodicUpdates()
                // getMyTrees();
            }
        }
    }


    /**
     * Called when the tree sync process completes (for one tree)
     * @param httpResponseCode
     *
     * We are not currently syncing trees, instead the app is for providing trees to the server and that's it
     */
    fun onTreeSyncResult(result: Boolean, httpResponseCode: Int, responseBody: String) {
        Log.i("MainActivity", "onTreeSyncedResult($result)")
        Log.i("MainActivity", "httpResponseCode(" + Integer.toString(httpResponseCode) + ")")
        // Hide the progress dialog

        if (result) {

            val jsonReponse: JSONObject
            when (httpResponseCode) {
                HttpStatus.SC_OK ->
                    //successfull sync, save the token and continue

                    try {
                        jsonReponse = JSONObject(responseBody)

                        Log.e("response body", jsonReponse.toString())

                    } catch (e: JSONException) {
                        // TODO Auto-generated catch block
                        e.printStackTrace()

                        Log.e("response body", responseBody)
                    }


                else -> {
                }
            }

        } else {
            Log.e("MainActivity", "onLoginResult: failed to login")
            when (httpResponseCode) {
                HttpStatus.SC_UNAUTHORIZED -> Toast.makeText(this@MainActivity, "Incorrect username or password.", Toast.LENGTH_SHORT).show()

                -1000 -> Toast.makeText(this@MainActivity, "Please check your internet connection and try again.", Toast.LENGTH_SHORT).show()

                else -> {
                }
            }
        }
    }

    /**
     * In response to a request to start updates, send a request
     * to Location Services
     */
    private fun startPeriodicUpdates() {

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (mLocationUpdatesStarted) {
            return
        }

        locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        mLocationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                this@MainActivity.onLocationChanged(location)
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        // Register the listener with Location Manager's network provider
        locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, mLocationListener)

        mLocationUpdatesStarted = true
    }


    /**
     * In response to a request to stop updates, send a request to
     * Location Services
     */
    private fun stopPeriodicUpdates() {
        if (locationManager != null) {
            locationManager!!.removeUpdates(mLocationListener)
            mLocationListener = null
        }
        mLocationUpdatesStarted = false

    }


    override fun refreshMap() {
        startPeriodicUpdates()
    }

    private fun getMyTrees() {

        mDataManager = object : DataManager<List<UserTree>>() {
            override fun onDataLoaded(data: List<UserTree>?) {
                userTrees = data
                mDatabaseManager!!.openDatabase()
                val userId = mSharedPreferences!!.getLong(ValueHelper.MAIN_USER_ID, -1)
                for (userTree in data!!) {
                    val values = ContentValues()
                    values.put("tree_id", userTree.id)
                    values.put("user_id", java.lang.Long.toString(userId))
                    mDatabaseManager!!.insert("pending_updates", null, values)
                }
                mDatabaseManager!!.closeDatabase()

                if (data.size > 0) {
                    Log.d("MainActivity", "GetMyTreesTask onPostExecute jsonReponseArray.length() > 0")

                    var bundle = intent.extras

                    if (bundle == null)
                        bundle = Bundle()

                    bundle.putBoolean(ValueHelper.RUN_FROM_HOME_ON_LOGIN, true)

                    fragment = DataFragment()
                    fragment!!.arguments = bundle

                    fragmentTransaction = supportFragmentManager
                            .beginTransaction()
                    fragmentTransaction!!.replace(R.id.container_fragment, fragment).addToBackStack(ValueHelper.DATA_FRAGMENT).commit()
                    Log.d("MainActivity", "click back, back stack count: " + supportFragmentManager.backStackEntryCount)
                    for (entry in 0 until supportFragmentManager.backStackEntryCount) {
                        Log.d("MainActivity", "Found fragment: " + supportFragmentManager.getBackStackEntryAt(entry).name)
                    }
                }
            }

            override fun onRequestFailed(message: String?) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        Log.d("MainActivity", "getMyTrees")
        mDataManager!!.loadUserTrees()
    }

    companion object {

        private val TAG = "MainActivity"

        val mHandler = Handler()

        lateinit var dbHelper: DbHelper

        var mCurrentLocation: Location? = null
        var mCurrentTreeLocation: Location? = null

        var syncDataFromExitScreen = false

        var mAllowNewTreeOrUpdate = false

        var progressDialog: ProgressDialog? = null
    }

}

