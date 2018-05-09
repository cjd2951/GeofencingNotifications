package com.whitlatch.kotlingeofencealexandchris

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson

/**
 * Created by cdavis.
 */
class FencesAdapter(context: Context, fences: ArrayList<Fence>) : ArrayAdapter<Fence>(context, 0, fences) {

    private val SELECTED_FENCE = "SELECTED_FENCE"
    private val TAG = "FencePickerActivity"
    private val FENCE_LIST = "FENCE_LIST"

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        // Get the data item for this position
        val fence = getItem(position)
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.fragment_fence, parent, false)
        }
        val fenceLabel: TextView = convertView!!.findViewById<TextView>(R.id.fence_list_label) as TextView
        val toggleSwitch: Switch = convertView.findViewById<Switch>(R.id.fence_enable_switch)

        fenceLabel.text = fence.name
        toggleSwitch.isChecked = fence.enabled

        val activity: Activity = context as Activity

        fenceLabel.setOnClickListener(View.OnClickListener {
            val selectedFence: Fence = getItem(position)
            openFenceEditFragment(selectedFence)
        })

        toggleSwitch.setOnCheckedChangeListener({ _, isChecked ->
            val selectedFence: Fence = getItem(position)
            selectedFence.enabled = isChecked
            updateFence(selectedFence)
        })

        return convertView
    }


    fun openFenceEditFragment(fence: Fence){

        val bundle = Bundle()
        val gson: Gson = Gson()

        bundle.putString(SELECTED_FENCE,gson.toJson(fence))
        val fragInfo = FenceEditFragment()
        fragInfo.arguments = bundle
        val manager = (context as FragmentActivity).supportFragmentManager

        val transaction = manager.beginTransaction()
        transaction.replace(R.id.fence_fragment_container, fragInfo)
        transaction.commit()


    }

    fun returnColorSelection(activity: Activity, selectedFence: Fence){
        val returnIntent = Intent()
        val gson: Gson = Gson()

        returnIntent.putExtra(SELECTED_FENCE,gson.toJson(selectedFence))
        activity.setResult(Activity.RESULT_OK, returnIntent)
        activity.finish()
    }


    fun updateFence(fence: Fence){
        // Add a new document with a generated ID
        FirebaseApp.initializeApp(context);
        val db = FirebaseFirestore.getInstance()
        db.collection("fences").document(fence.id)
                .update(fence.toMap())
                .addOnSuccessListener {
                    documentReference ->
                    Log.d(TAG, "Fence ${fence.id} updated")
                    Toast.makeText(context, "Fence (${fence.name}) updated.", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }


    }





}