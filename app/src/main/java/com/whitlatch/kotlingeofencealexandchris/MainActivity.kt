package com.whitlatch.kotlingeofencealexandchris

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Debug
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Created by alexw on 4/28/2018.
 */
class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, ResultCallback<Status> {

    private var map: GoogleMap? = null
    private var googleApiClient: GoogleApiClient? = null
    private var lastLocation: Location? = null

    private var textLat: TextView? = null
    private var textLong: TextView? = null

    private var mapFragment: MapFragment? = null

    private val REQ_PERMISSION = 999

    private var locationRequest: LocationRequest? = null
    // Defined in mili seconds.
    // This number in extremely low, and should be used only for debug
    private val UPDATE_INTERVAL = 1000
    private val FASTEST_INTERVAL = 900

    private var locationMarker: Marker? = null


    private var geoFenceMarker: Marker? = null

    private val geoFencePendingIntent: PendingIntent? = null
    private val GEOFENCE_REQ_CODE = 0

    // Draw Geofence circle on GoogleMap
    private var geoFenceLimits: Circle? = null

    private val KEY_GEOFENCE_LAT = "GEOFENCE LATITUDE"
    private val KEY_GEOFENCE_LON = "GEOFENCE LONGITUDE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textLat = findViewById(R.id.lat)
        textLong = findViewById(R.id.lon)

        // initialize GoogleMaps
        initGMaps()

        // create GoogleApiClient
        createGoogleApi()
    }

    // Create GoogleApiClient instance
    private fun createGoogleApi() {
        Log.d(TAG, "createGoogleApi()")
        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build()
        }
    }

    override fun onStart() {
        super.onStart()

        // Call GoogleApiClient connection when starting the Activity
        googleApiClient!!.connect()
    }

    override fun onStop() {
        super.onStop()

        // Disconnect GoogleApiClient when stopping Activity
        googleApiClient!!.disconnect()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.geofence -> {
                showDialog()
                //startGeofence()
                return true
            }
            R.id.clear -> {
                showClearDialog()
                //clearGeofence()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Check for permission to access Location
    private fun checkPermission(): Boolean {
        Log.d(TAG, "checkPermission()")
        // Ask for permission if it wasn't granted yet
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Asks for permission
    private fun askPermission() {
        Log.d(TAG, "askPermission()")
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_PERMISSION
        )
    }

    // Verify user's response of the permission requested
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult()")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    getLastKnownLocation()

                } else {
                    // Permission denied
                    permissionsDenied()
                }
            }
        }
    }

    // App cannot work without the permissions
    private fun permissionsDenied() {
        Log.w(TAG, "permissionsDenied()")
        // TODO close app and warn user
    }

    // Initialize GoogleMaps
    private fun initGMaps() {
        mapFragment = fragmentManager.findFragmentById(R.id.map) as MapFragment
        mapFragment!!.getMapAsync(this)
    }

    // Callback called when Map is ready
    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "onMapReady()")
        map = googleMap
        map!!.setOnMapClickListener(this)
        map!!.setOnMarkerClickListener(this)
    }

    override fun onMapClick(latLng: LatLng) {
        Log.d(TAG, "onMapClick($latLng)")
        markerForGeofence(latLng)
        Log.i("Clicked map", "Clicked map")
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        Log.d(TAG, "onMarkerClickListener: " + marker.position)
        return false
    }

    // Start location Updates
    private fun startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()")
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL.toLong())
                .setFastestInterval(FASTEST_INTERVAL.toLong())

        if (checkPermission())
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this)
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "onLocationChanged [$location]")
        lastLocation = location
        writeActualLocation(location)
    }

    // GoogleApiClient.ConnectionCallbacks connected
    override fun onConnected(bundle: Bundle?) {
        Log.i(TAG, "onConnected()")
        getLastKnownLocation()
        recoverGeofenceMarker()
    }

    // GoogleApiClient.ConnectionCallbacks suspended
    override fun onConnectionSuspended(i: Int) {
        Log.w(TAG, "onConnectionSuspended()")
    }

    // GoogleApiClient.OnConnectionFailedListener fail
    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.w(TAG, "onConnectionFailed()")
    }

    // Get last known location
    private fun getLastKnownLocation() {
        Log.d(TAG, "getLastKnownLocation()")
        if (checkPermission()) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient)
            if (lastLocation != null) {
                Log.i(TAG, "LasKnown location. " +
                        "Long: " + lastLocation!!.longitude +
                        " | Lat: " + lastLocation!!.latitude)
                writeLastLocation()
                startLocationUpdates()
            } else {
                Log.w(TAG, "No location retrieved yet")
                startLocationUpdates()
            }
        } else
            askPermission()
    }

    private fun writeActualLocation(location: Location?) {
        textLat!!.text = "Lat: " + location!!.latitude
        textLong!!.text = "Long: " + location.longitude

        markerLocation(LatLng(location.latitude, location.longitude))
    }

    private fun writeLastLocation() {
        writeActualLocation(lastLocation)
    }

    private fun markerLocation(latLng: LatLng) {
        Log.i(TAG, "markerLocation($latLng)")
        val title = latLng.latitude.toString() + ", " + latLng.longitude
        val markerOptions = MarkerOptions()
                .position(latLng)
                .title(title)
        if (map != null) {
            if (locationMarker != null)
                locationMarker!!.remove()
            locationMarker = map!!.addMarker(markerOptions)
            val zoom = 14f
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom)
            map!!.animateCamera(cameraUpdate)
        }
    }

    private fun markerForGeofence(latLng: LatLng) {
        Log.i(TAG, "markerForGeofence($latLng)")
        val title = latLng.latitude.toString() + ", " + latLng.longitude
        // Define marker options
        val markerOptions = MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .title(title)
        if (map != null) {
            // Remove last geoFenceMarker
            if (geoFenceMarker != null)
                geoFenceMarker!!.remove()

            geoFenceMarker = map!!.addMarker(markerOptions)

        }
    }

    // Start Geofence creation process
    private fun startGeofence() {
        Log.i(TAG, "startGeofence()")
        if (geoFenceMarker != null) {
            val geofence = createGeofence(geoFenceMarker!!.position, GEOFENCE_RADIUS)
            val geofenceRequest = createGeofenceRequest(geofence)
            addGeofence(geofenceRequest)
        } else {
            Log.e(TAG, "Geofence marker is null")
        }
    }

    // Create a Geofence
    private fun createGeofence(latLng: LatLng, radius: Float): Geofence {
        Log.d(TAG, "createGeofence")
        return Geofence.Builder()
                .setRequestId(GEOFENCE_REQ_ID)
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration(GEO_DURATION)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
    }

    // Create a Geofence Request
    private fun createGeofenceRequest(geofence: Geofence): GeofencingRequest {
        Log.d(TAG, "createGeofenceRequest")
        return GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()
    }

    private fun createGeofencePendingIntent(): PendingIntent {
        Log.d(TAG, "createGeofencePendingIntent")
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent

        val intent = Intent(this, GeofenceTransitionService::class.java)
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private fun addGeofence(request: GeofencingRequest) {
        Log.d(TAG, "addGeofence")
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    googleApiClient,
                    request,
                    createGeofencePendingIntent()
            ).setResultCallback(this)
    }

    override fun onResult(status: Status) {
        Log.i(TAG, "onResult: " + status)
        if (status.isSuccess) {
            saveGeofence()
            drawGeofence()
            Log.i("", "Drawing Geofence! - ALEX MSG")
        } else {
            // inform about fail
        }
    }

    private fun drawGeofence() {
        Log.d(TAG, "drawGeofence()")

        if (geoFenceLimits != null)
            geoFenceLimits!!.remove()

        val circleOptions = CircleOptions()
                .center(geoFenceMarker!!.position)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS.toDouble())
        geoFenceLimits = map!!.addCircle(circleOptions)
        Log.i("Geofence", "Geofence has been drawn! -msg by alex")
    }

    // Saving GeoFence marker with prefs mng
    private fun saveGeofence() {
        Log.d(TAG, "saveGeofence()")
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        editor.putLong(KEY_GEOFENCE_LAT, java.lang.Double.doubleToRawLongBits(geoFenceMarker!!.position.latitude))
        editor.putLong(KEY_GEOFENCE_LON, java.lang.Double.doubleToRawLongBits(geoFenceMarker!!.position.longitude))
        editor.apply()
    }

    // Recovering last Geofence marker
    private fun recoverGeofenceMarker() {
        Log.d(TAG, "recoverGeofenceMarker")
        val sharedPref = getPreferences(Context.MODE_PRIVATE)

        if (sharedPref.contains(KEY_GEOFENCE_LAT) && sharedPref.contains(KEY_GEOFENCE_LON)) {
            val lat = java.lang.Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LAT, -1))
            val lon = java.lang.Double.longBitsToDouble(sharedPref.getLong(KEY_GEOFENCE_LON, -1))
            val latLng = LatLng(lat, lon)
            markerForGeofence(latLng)
            drawGeofence()
        }
    }

    // Clear Geofence
    private fun clearGeofence() {
        Log.d(TAG, "clearGeofence()")
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent()
        ).setResultCallback { status ->
            if (status.isSuccess) {
                // remove drawing
                removeGeofenceDraw()
            }
        }
    }

    private fun removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()")
        if (geoFenceMarker != null)
            geoFenceMarker!!.remove()
        if (geoFenceLimits != null)
            geoFenceLimits!!.remove()
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName

        private val NOTIFICATION_MSG = "NOTIFICATION MSG"
        // Create a Intent send by the notification
        fun makeNotificationIntent(context: Context, msg: String): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(NOTIFICATION_MSG, msg)
            return intent
        }

        private val GEO_DURATION = (60 * 60 * 1000).toLong()
        private val GEOFENCE_REQ_ID = "My Geofence"
        private val GEOFENCE_RADIUS = 500.0f // in meters
    }

    fun showDialog(){
        var builder :AlertDialog.Builder = AlertDialog.Builder(this)
        var inflater: LayoutInflater = layoutInflater
        var view : View = inflater.inflate(R.layout.dialog_box,null)
        builder.setView(view)
        builder.setNegativeButton("Cancel", object : DialogInterface.OnClickListener{
            override fun onClick(dialog: DialogInterface?, which: Int) {
                Toast.makeText(this@MainActivity, "Geofence creation canceled",Toast.LENGTH_LONG).show()
                dialog!!.dismiss()

            }
        })

        builder.setPositiveButton("Create", object : DialogInterface.OnClickListener{
            override fun onClick(dialog: DialogInterface?, which: Int) {
                Toast.makeText(this@MainActivity, "Geofence creation successful",Toast.LENGTH_LONG).show()
                dialog!!.dismiss()
                startGeofence()
            }
        })
        var dialog: Dialog = builder.create()
        dialog.show()
    }

    fun showClearDialog(){
        var builder :AlertDialog.Builder = AlertDialog.Builder(this)
        var inflater: LayoutInflater = layoutInflater
        var view : View = inflater.inflate(R.layout.dialog_box_clear,null)
        builder.setView(view)
        builder.setNegativeButton("No", object : DialogInterface.OnClickListener{
            override fun onClick(dialog: DialogInterface?, which: Int) {
                Toast.makeText(this@MainActivity, "Geofence Clear Canceled",Toast.LENGTH_LONG).show()
                dialog!!.dismiss()
            }
        })

        builder.setPositiveButton("Yes", object : DialogInterface.OnClickListener{
            override fun onClick(dialog: DialogInterface?, which: Int) {
                Toast.makeText(this@MainActivity, "Removing All Created Geofences",Toast.LENGTH_LONG).show()
                dialog!!.dismiss()
                clearGeofence()
            }
        })
        var dialog: Dialog = builder.create()
        dialog.show()
    }


}