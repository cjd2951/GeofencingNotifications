package com.whitlatch.kotlingeofencealexandchris

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Created by cdavis.
 */

class FenceFragment : Fragment() {

    private var mListView: ListView? = null

    var fenceList: MutableList<Fence> = arrayListOf<Fence>()

    //    private var db: FirebaseFirestore? = null
    private val FENCE_LIST = "FENCE_LIST"
    private var mFirebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mListView = inflater.inflate(R.layout.fragment_geofence_list, container, false) as ListView?;
        val fences: String ?= arguments?.getString(FENCE_LIST)
        val gson: Gson = Gson()

        val contacts: List<Fence>
        val listType = object : TypeToken<List<Fence>>() {

        }.type

        if(null != fences){
            val convertedColors = gson.fromJson<ArrayList<Fence>>(fences, listType)
            fenceList = convertedColors
        }

        val adapter = FencesAdapter(context, fenceList as ArrayList<Fence>)
        mListView!!.adapter = adapter
        return mListView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        fenceList.clear()
    }



}
