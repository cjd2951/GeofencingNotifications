package com.whitlatch.kotlingeofencealexandchris

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.graphics.Color
import android.location.Location
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationListener
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_manage_geofence.*
import kotlinx.android.synthetic.main.fragment_manage_geofence.view.*

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [FenceEditFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [FenceEditFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class FenceEditFragment : Fragment(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, ResultCallback<Status> {


    private var selectedFence = Fence();
    private var db: FirebaseFirestore? = null
    val fences: MutableList<Fence> = arrayListOf<Fence>()

    private val SELECTED_FENCE = "SELECTED_FENCE"
    private val TAG = "FenceEditFragment"
    private var map: GoogleMap? = null
    private var googleApiClient: GoogleApiClient? = null

    private var mapFragment: SupportMapFragment? = null

    private val REQ_PERMISSION = 999

    private var locationRequest: LocationRequest? = null
    // Defined in mili seconds.
    // This number in extremely low, and should be used only for debug
    private val UPDATE_INTERVAL = 1000
    private val FASTEST_INTERVAL = 900

    private var locationMarker: Marker? = null
    private var lastLocation: Location? = null


    private var geoFenceMarker: Marker? = null

    // Draw Geofence circle on GoogleMap
    private var geoFenceLimits: Circle? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_manage_geofence, container, false)
        createGoogleApi()
        initGMaps()
        FirebaseApp.initializeApp(view.context);
        db = FirebaseFirestore.getInstance()


        if(null != arguments?.getString(SELECTED_FENCE)){
            val fence: String ?= arguments?.getString(SELECTED_FENCE)
            val gson: Gson = Gson()

            if(null != fence){
                val convertedFence = gson.fromJson<Fence>(fence, Fence::class.java)
                setSelectedFenceValues(convertedFence, view)
                selectedFence = convertedFence
            }
        }



        val button = view.findViewById<Button>(R.id.fence_save_button)
        button.setOnClickListener(object : View.OnClickListener{
            override fun onClick(v: View) {
                // do something
                upsertColor()
            }
        })

        val textInput = view.findViewById<EditText>(R.id.name_input)
        textInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                selectedFence.name = textInput.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        val latInput = view.findViewById<EditText>(R.id.lat_input)
        latInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(latInput.text.isBlank()){
                    selectedFence.latitude = 0.0
                }else{
                    selectedFence.latitude = latInput.text.toString().toDouble()
                }
                markerForGeofence(LatLng(selectedFence.latitude, selectedFence.longitude))
                drawGeofence()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        val lonInput = view.findViewById<EditText>(R.id.lon_input)
        lonInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(lonInput.text.isBlank()){
                    selectedFence.longitude = 0.0
                }else{
                    selectedFence.longitude = lonInput.text.toString().toDouble()
                }
                markerForGeofence(LatLng(selectedFence.latitude, selectedFence.longitude))
                drawGeofence()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        val radInput = view.findViewById<EditText>(R.id.rad_input)
        radInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(radInput.text.isBlank()){
                    selectedFence.radius = 0
                }else{
                    selectedFence.radius = radInput.text.toString().toInt()
                }
                drawGeofence()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

        val notInput = view.findViewById<EditText>(R.id.not_input)
        notInput.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                selectedFence.notificationText = notInput.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
        })

//        redSeekBar.setOnSeekBarChangeListener(redColorChangeListener())
//        greenSeekBar.setOnSeekBarChangeListener(greenColorChangeListener())
//        blueSeekBar.setOnSeekBarChangeListener(blueColorChangeListener())

        return view
    }

    protected fun getDb(): FirebaseFirestore?{
        return this.db;
    }


    fun upsertColor(){
        if(selectedFence.id.isBlank()){
            createFence(selectedFence)
        }else{
            updateFence(selectedFence)
        }
    }

    fun resetDataFields(){
        selectedFence = Fence()
        name_input.text.clear()
        lat_input.text.clear()
        lon_input.text.clear()
        rad_input.text.clear()
        not_input.text.clear()
    }

    fun setSelectedFenceValues(fence: Fence, view: View){
        selectedFence = fence
        view.name_input.setText(fence.name)
        view.lat_input.setText(fence.latitude.toString())
        view.lon_input.setText(fence.longitude.toString())
        view.rad_input.setText(fence.radius.toString())
        view.not_input.setText(fence.notificationText)

    }

    fun createFence(fence: Fence){
        // Add a new document with a generated ID
        db!!.collection("fences")
                .add(fence.toMap())
                .addOnSuccessListener {
                    documentReference ->
                    Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.id)
                    selectedFence.id = documentReference.id
                    Toast.makeText(context, "Fence (${fence.name}) created.", Toast.LENGTH_LONG).show()
                    resetDataFields()
                }
                .addOnFailureListener { e -> Log.w(TAG, "Error adding document", e) }


    }

    fun updateFence(fence: Fence){
        // Add a new document with a generated ID

        db!!.collection("fences").document(fence.id)
                .update(fence.toMap())
                .addOnSuccessListener {
                    documentReference ->
                    Log.d(TAG, "Fence ${fence.id} updated")
                    Toast.makeText(context, "Fence (${fence.name}) updated.", Toast.LENGTH_LONG).show()
                    resetDataFields()
                }
                .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }


    }


    /*
    Start GMAPS Methods
     */
    // Initialize GoogleMaps
    private fun initGMaps() {
        mapFragment = childFragmentManager.findFragmentById(R.id.mapfrag) as SupportMapFragment
        mapFragment!!.getMapAsync(this)
    }

    // Callback called when Map is ready
    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "onMapReady()")
        map = googleMap
        map!!.setOnMapClickListener(this)
        map!!.setOnMarkerClickListener(this)
        markerForGeofence(LatLng(selectedFence.latitude, selectedFence.longitude))
        drawGeofence()
    }

    override fun onMapClick(latLng: LatLng) {
        Log.d(TAG, "onMapClick($latLng)")
        markerForGeofenceForTextUpdates(latLng)
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
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest,this)
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

        markerLocation(LatLng(location!!.latitude, location.longitude))
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
            selectedFence.latitude = latLng.latitude
            selectedFence.longitude = latLng.longitude

        }
    }

    private fun markerForGeofenceForTextUpdates(latLng: LatLng) {
            markerForGeofence(latLng)
            updateSelectedFenceValues()
    }

    private fun updateSelectedFenceValues(){
        lat_input.setText(selectedFence.latitude.toString())
        lon_input.setText(selectedFence.longitude.toString())
        rad_input.setText(selectedFence.radius.toString())
    }

    private fun drawGeofence() {
        Log.d(TAG, "drawGeofence()")

        if (geoFenceLimits != null)
            geoFenceLimits!!.remove()

        val latLng = LatLng(selectedFence.latitude, selectedFence.longitude)

        val circleOptions = CircleOptions()
                .center(latLng)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(selectedFence.radius.toDouble())
        geoFenceLimits = map!!.addCircle(circleOptions)
        Log.i("Geofence", "Geofence has been drawn! -msg by alex")
    }

    private fun removeGeofenceDraw() {
        Log.d(TAG, "removeGeofenceDraw()")
        if (geoFenceMarker != null)
            geoFenceMarker!!.remove()
        if (geoFenceLimits != null)
            geoFenceLimits!!.remove()
    }
    /*
    End GMAPS Methods
     */

    // Create GoogleApiClient instance
    private fun createGoogleApi() {
        Log.d(TAG, "createGoogleApi()")
        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(activity)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build()
        }
    }

    protected fun getGoogleApiClient(): GoogleApiClient?{
        return this.googleApiClient
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


    // Check for permission to access Location
    private fun checkPermission(): Boolean {
        Log.d(TAG, "checkPermission()")
        // Ask for permission if it wasn't granted yet
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Asks for permission
    private fun askPermission() {
        Log.d(TAG, "askPermission()")
        ActivityCompat.requestPermissions(
                activity,
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


    override fun onResult(status: Status) {
        Log.i(TAG, "onResult: " + status)
        if (status.isSuccess) {
            drawGeofence()
        } else {
            // inform about fail
        }
    }


}
