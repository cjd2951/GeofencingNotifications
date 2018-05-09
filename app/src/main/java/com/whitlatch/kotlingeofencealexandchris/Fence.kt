package com.whitlatch.kotlingeofencealexandchris

import com.google.firebase.database.Exclude
import java.util.HashMap

/**
 * Created by cdavis.
 */
class Fence(@get:Exclude var id: String, var name: String, var latitude: Double, var longitude: Double, var radius: Int, var enabled: Boolean, var notificationText: String){


    constructor() : this("","",0.0, 0.0, 0, false, "")


    @Exclude
    fun toMap(): Map<String, Any> {
        val result = HashMap<String, Any>()
        result["id"] = id
        result["name"] = name
        result["latitude"] = latitude
        result["longitude"] = longitude
        result["radius"] = radius
        result["enabled"] = enabled
        result["notificationText"] = notificationText

        return result
    }



}