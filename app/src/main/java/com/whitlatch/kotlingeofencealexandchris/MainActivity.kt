package com.whitlatch.kotlingeofencealexandchris

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.AsyncTask
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

/**
 * Created by alexw on 4/28/2018.
 */
class MainActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status>{




//    private var textLat: TextView? = null
//    private var textLong: TextView? = null

    private var googleApiClient: GoogleApiClient? = null

    private val geoFencePendingIntent: PendingIntent? = null
    private val GEOFENCE_REQ_CODE = 0

    private val KEY_GEOFENCE_LAT = "GEOFENCE LATITUDE"
    private val KEY_GEOFENCE_LON = "GEOFENCE LONGITUDE"

    private val GEOFENCE_JSON_KEY = "GEOFENCE_JSON"

    private var db: FirebaseFirestore? = null

    private val FENCE_LIST = "FENCE_LIST"




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main)

        // create GoogleApiClient
        createGoogleApi()
        db = FirebaseFirestore.getInstance()
        LoadGeofencesThread().execute()


        openFenceEditFragment()


    }


    fun openFenceEditFragment(){

        val fragInfo = FenceEditFragment()

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fence_fragment_container, fragInfo)
        transaction.commit()


    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.manageFence -> {
                openFenceListFragment()
                //startGeofence()
                return true
            }
            R.id.createFence -> {
                openFenceEditFragment()
                //clearGeofence()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }





    // Start Geofence creation process
    private fun startGeofence(fence: Fence) {
        Log.i(TAG, "startGeofence()")
        val position = LatLng(fence.latitude, fence.longitude)
        val geofence = createGeofence(position, fence.radius.toFloat(), fence.name)
        val geofenceRequest = createGeofenceRequest(geofence)
        addGeofence(geofenceRequest, fence)
    }

    // Create a Geofence
    private fun createGeofence(latLng: LatLng, radius: Float, fenceName: String): Geofence {
        Log.d(TAG, "createGeofence")
        return Geofence.Builder()
                .setRequestId(fenceName)
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

    private fun createGeofencePendingIntent(fence: Fence): PendingIntent {
        Log.d(TAG, "createGeofencePendingIntent")
        if (geoFencePendingIntent != null)
            return geoFencePendingIntent
        val gson = Gson()
        val intent = Intent(this, GeofenceTransitionService::class.java)
        intent.putExtra(GEOFENCE_JSON_KEY,gson.toJson(fence))
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private fun addGeofence(request: GeofencingRequest, fence: Fence) {
        Log.d(TAG, "addGeofence")
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    googleApiClient,
                    request,
                    createGeofencePendingIntent(fence)
            ).setResultCallback(this)
    }

    override fun onResult(status: Status) {
        Log.i(TAG, "onResult: " + status)
        if (status.isSuccess) {
//            saveGeofence()
//            drawGeofence()
        } else {
            // inform about fail
        }
    }

    // Check for permission to access Location
    private fun checkPermission(): Boolean {
        Log.d(TAG, "checkPermission()")
        // Ask for permission if it wasn't granted yet
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }




    // Clear Geofence
    private fun clearGeofence(fence: Fence) {
        Log.d(TAG, "clearGeofence()")
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                createGeofencePendingIntent(fence)
        ).setResultCallback { status ->
            if (status.isSuccess) {
            }
        }
    }

    // Create GoogleApiClient instance
    private fun createGoogleApi() {
        Log.d(MainActivity.TAG, "createGoogleApi()")
        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(this)
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

    // GoogleApiClient.ConnectionCallbacks connected
    override fun onConnected(bundle: Bundle?) {
        Log.i(TAG, "onConnected()")
    }

    // GoogleApiClient.ConnectionCallbacks suspended
    override fun onConnectionSuspended(i: Int) {
        Log.w(TAG, "onConnectionSuspended()")
    }

    // GoogleApiClient.OnConnectionFailedListener fail
    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.w(TAG, "onConnectionFailed()")
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
        private val GEOFENCE_REQ_ID = "Home (Lock Door)"
        private val GEOFENCE_RADIUS = 500.0f // in meters
    }

//    fun showDialog(){
//        var builder :AlertDialog.Builder = AlertDialog.Builder(this)
//        var inflater: LayoutInflater = layoutInflater
//        var view : View = inflater.inflate(R.layout.dialog_box,null)
//        builder.setView(view)
//        builder.setNegativeButton("Cancel", object : DialogInterface.OnClickListener{
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                Toast.makeText(this@MainActivity, "Geofence creation canceled",Toast.LENGTH_LONG).show()
//                dialog!!.dismiss()
//
//            }
//        })
//
//        builder.setPositiveButton("Create", object : DialogInterface.OnClickListener{
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                Toast.makeText(this@MainActivity, "Geofence creation successful",Toast.LENGTH_LONG).show()
//                dialog!!.dismiss()
//                startGeofence()
//            }
//        })
//        var dialog: Dialog = builder.create()
//        dialog.show()
//    }

//    fun showClearDialog(){
//        var builder :AlertDialog.Builder = AlertDialog.Builder(this)
//        var inflater: LayoutInflater = layoutInflater
//        var view : View = inflater.inflate(R.layout.dialog_box_clear,null)
//        builder.setView(view)
//        builder.setNegativeButton("No", object : DialogInterface.OnClickListener{
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                Toast.makeText(this@MainActivity, "Geofence Clear Canceled",Toast.LENGTH_LONG).show()
//                dialog!!.dismiss()
//            }
//        })
//
//        builder.setPositiveButton("Yes", object : DialogInterface.OnClickListener{
//            override fun onClick(dialog: DialogInterface?, which: Int) {
//                Toast.makeText(this@MainActivity, "Removing All Created Geofences",Toast.LENGTH_LONG).show()
//                dialog!!.dismiss()
//                clearGeofence()
//            }
//        })
//        var dialog: Dialog = builder.create()
//        dialog.show()
//    }



    inner class LoadGeofencesThread: AsyncTask<String, String, String>() {

        private var fences = arrayListOf<Fence>()

        override fun doInBackground(vararg arg: String?):String {
            Log.i(TAG, "LoadGeofencesThread")
            Thread.sleep(5000)
            getFencesFromDb()
            return ""

        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
        }

        fun getFencesFromDb(){
            db!!.collection("fences")
                    .get()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            for ((index, document) in task.result.withIndex()) {
                                Log.d(TAG, document.id + " => " + document.data)
                                val tempFence: Fence = document.toObject(Fence::class.java)
                                tempFence.id = document.id
                                Log.d(TAG, "FENCE_FROM_DB: ${tempFence.id}:${tempFence.name}")
                                clearGeofence(tempFence)
                                if(tempFence.enabled){
                                    startGeofence(tempFence)
                                }

                            }

                        } else {
                            Log.d(TAG, "Error getting documents: ", task.exception)
                        }
                    }

        }
    }



    fun openFenceListFragment(){
        val tempFences = arrayListOf<Fence>()
        db!!.collection("fences")
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        for ((index, document) in task.result.withIndex()) {
                            Log.d(TAG, document.id + " => " + document.data)
                            val tempFence: Fence = document.toObject(Fence::class.java)
                            tempFence.id = document.id
                            Log.d(TAG, "FENCE_FROM_DB: ${tempFence.id}:${tempFence.name}")
                            tempFences.add(tempFence)
                        }
                        val bundle = Bundle()
                        val gson: Gson = Gson()

                        bundle.putString(FENCE_LIST,gson.toJson(tempFences))
                        val fragInfo = FenceFragment()
                        fragInfo.arguments = bundle

                        val transaction = supportFragmentManager.beginTransaction()
                        transaction.replace(R.id.fence_fragment_container, fragInfo)
                        transaction.commit()
                    } else {
                        Log.d(TAG, "Error getting documents: ", task.exception)
                    }
                }
    }


}