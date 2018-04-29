package com.whitlatch.kotlingeofencealexandchris

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.support.v4.app.NotificationCompat
import android.text.TextUtils
import android.util.Log

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

import java.util.ArrayList
/**
 * Created by alexw on 4/28/2018.
 */


class GeofenceTrasitionService : IntentService(GeofenceTrasitionService::class.java.simpleName) {

    override fun onHandleIntent(intent: Intent?) {
        Log.i("Alex msg", "onHandleIntent() from TransitionService")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        // Handling errors
        if (geofencingEvent.hasError()) {
            val errorMsg = getErrorString(geofencingEvent.errorCode)
            Log.e(TAG, errorMsg)
            return
        }

        val geoFenceTransition = geofencingEvent.geofenceTransition
        // Check if the transition type is of interest
        if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geoFenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // Get the geofence that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            val geofenceTransitionDetails = getGeofenceTrasitionDetails(geoFenceTransition, triggeringGeofences)

            // Send notification details as a String
            sendNotification(geofenceTransitionDetails)
        }
    }


    private fun getGeofenceTrasitionDetails(geoFenceTransition: Int, triggeringGeofences: List<Geofence>): String {
        // get the ID of each geofence triggered
        val triggeringGeofencesList = ArrayList<String>()
        for (geofence in triggeringGeofences) {
            triggeringGeofencesList.add(geofence.requestId)
        }

        var status: String? = null
        if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER)
            status = "Entering "
        else if (geoFenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT)
            status = "Exiting "
        return status!! + TextUtils.join(", ", triggeringGeofencesList)
    }

    private fun sendNotification(msg: String) {
        Log.i(TAG, "sendNotification: " + msg)

        // Intent to start the main Activity
        val notificationIntent = MainActivity.makeNotificationIntent(
                applicationContext, msg
        )

        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addParentStack(MainActivity::class.java)
        stackBuilder.addNextIntent(notificationIntent)
        val notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)


        // Creating and sending Notification
        val notificatioMng = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificatioMng.notify(
                GEOFENCE_NOTIFICATION_ID,
                createNotification(msg, notificationPendingIntent))

    }

    // Create notification
    private fun createNotification(msg: String, notificationPendingIntent: PendingIntent): Notification {
        val notificationBuilder = NotificationCompat.Builder(this)
        notificationBuilder
                .setSmallIcon(R.drawable.ic_action_location)
                .setColor(Color.RED)
                .setContentTitle(msg)
                .setContentText("Geofence Notification!")
                .setContentIntent(notificationPendingIntent)
                .setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE or Notification.DEFAULT_SOUND)
                .setAutoCancel(true)
        return notificationBuilder.build()
    }

    companion object {

        private val TAG = GeofenceTrasitionService::class.java.simpleName

        val GEOFENCE_NOTIFICATION_ID = 0


        private fun getErrorString(errorCode: Int): String {
            when (errorCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> return "GeoFence not available"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> return "Too many GeoFences"
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> return "Too many pending intents"
                else -> return "Unknown error."
            }
        }
    }
}