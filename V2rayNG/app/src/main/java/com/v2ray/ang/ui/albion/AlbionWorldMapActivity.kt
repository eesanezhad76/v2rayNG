package com.v2ray.ang.ui.albion

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.v2ray.ang.core.CoreServiceManager

/** Live local-coordinate map fed by the daemon-owned knowledge store. */
class AlbionWorldMapActivity : Activity() {
    private lateinit var mapView: AlbionWorldMapView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!::mapView.isInitialized) return
            val json = CoreServiceManager.getAlbionLiveKnowledgeJson()
            val valid = mapView.setSnapshot(json)
            status.text = if (valid) mapView.statusText() else "Invalid live knowledge snapshot"
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Albion live coordinate map"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        status = TextView(this).apply {
            textSize = 12f
            setPadding(4, 4, 4, 8)
            text = "Waiting for live observations"
        }
        mapView = AlbionWorldMapView(this)
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(mapView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        super.onDestroy()
    }
}
