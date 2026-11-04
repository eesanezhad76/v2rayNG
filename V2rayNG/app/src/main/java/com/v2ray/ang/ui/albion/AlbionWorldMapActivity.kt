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
    private lateinit var regionScanButton: android.widget.Button
    private var regionScanStartedAtMs = 0L
    @Volatile private var showingRegionScan = false
    private val handler = Handler(Looper.getMainLooper())
    private val dataThread = android.os.HandlerThread("AlbionWorldMapData").apply { start() }
    private val dataHandler = Handler(dataThread.looper)
    private val refreshInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var showingSavedCatalog = false
    @Volatile private var fitNextLiveSnapshot = false
    private val refresh = object : Runnable {
        override fun run() {
            if (!::mapView.isInitialized) return
            if (!refreshInFlight.compareAndSet(false, true)) {
                handler.postDelayed(this, 1500L)
                return
            }
            dataHandler.post {
                val valid = runCatching {
                    if (showingSavedCatalog) true
                    else if (showingRegionScan) mapView.setSnapshot(CoreServiceManager.getAlbionRegionScanJson())
                    else mapView.setSnapshot(CoreServiceManager.getAlbionLiveKnowledgeJson())
                }.getOrDefault(false)
                if (valid && !showingSavedCatalog && fitNextLiveSnapshot) {
                    mapView.fitToRegion()
                    fitNextLiveSnapshot = false
                }
                val statusText = if (!valid) "Invalid live knowledge snapshot"
                    else if (showingSavedCatalog) "SAVED CAPTURE • zone identity is not decoded • " + mapView.statusText()
                    else if (showingRegionScan) "REGION SCAN • " + (if (System.currentTimeMillis() < regionScanStartedAtMs + 60_000L) "collecting observations" else "finished and saved") + " • " + mapView.statusText()
                    else mapView.statusText()
                handler.post {
                    if (::status.isInitialized) status.text = statusText
                    if (::regionScanButton.isInitialized && showingRegionScan && !showingSavedCatalog) {
                        val active = System.currentTimeMillis() < regionScanStartedAtMs + 60_000L
                        regionScanButton.isEnabled = !active
                        regionScanButton.text = if (active) {
                            "SCANNING REGION • " + (((regionScanStartedAtMs + 60_000L - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1000L) + "s"
                        } else "SCAN NEXT REGION (60s)"
                    }
                    refreshInFlight.set(false)
                    if (::mapView.isInitialized) handler.postDelayed(this, 1500L)
                }
            }
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
            visibility = android.view.View.VISIBLE
            textSize = 12f
            setPadding(4, 4, 4, 8)
            text = "Waiting for live observations"
        }
        mapView = AlbionWorldMapView(this).apply { alpha = 0.5f }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val mapActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(android.widget.Button(this@AlbionWorldMapActivity).apply {
                text = "FIT ALL KNOWN"
                setOnClickListener { mapView.fitToRegion() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(android.widget.Button(this@AlbionWorldMapActivity).apply {
                text = "CENTER PLAYER"
                setOnClickListener { mapView.centerOnPlayer() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        mapActions.addView(android.widget.Button(this).apply {
            text = "SAVED CAPTURE"
            setOnClickListener {
                if (!showingSavedCatalog) {
                    val loaded = runCatching {
                        val catalog = assets.open("albion-static-catalog.json")
                            .bufferedReader().use { org.json.JSONObject(it.readText()) }
                        val rows = catalog.optJSONArray("entities") ?: org.json.JSONArray()
                        mapView.setSavedSnapshot(rows.toString()) && rows.length() > 0
                    }.getOrDefault(false)
                    if (loaded) {
                        showingSavedCatalog = true
                        regionScanButton.isEnabled = false
                        text = "RETURN LIVE"
                        mapView.fitToRegion()
                        status.text = "SAVED CAPTURE • zone identity is not decoded • " + mapView.statusText()
                    } else {
                        status.text = "Saved catalog could not be loaded"
                    }
                } else {
                    showingSavedCatalog = false
                    regionScanButton.isEnabled = !showingRegionScan || System.currentTimeMillis() >= regionScanStartedAtMs + 60_000L
                    fitNextLiveSnapshot = true
                    text = "SAVED CAPTURE"
                    handler.removeCallbacks(refresh)
                    handler.post(refresh)
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(mapActions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        regionScanButton = android.widget.Button(this).apply {
            text = "SCAN THIS REGION (60s)"
            setOnClickListener {
                val id = CoreServiceManager.startAlbionRegionScan()
                if (id == null) {
                    status.text = "Start packet capture first; region scanning needs the live capture worker."
                } else {
                    regionScanStartedAtMs = System.currentTimeMillis()
                    showingRegionScan = true
                    showingSavedCatalog = false
                    isEnabled = false
                    text = "SCANNING REGION • 60s"
                    fitNextLiveSnapshot = true
                    status.text = "Region scan started. Keep playing/moving through the area for 60 seconds so the game can send entity updates. Scan ID: $id"
                    handler.removeCallbacks(refresh)
                    handler.post(refresh)
                }
            }
        }
        root.addView(regionScanButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 6 })
        root.addView(mapView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        dataHandler.removeCallbacksAndMessages(null)
        dataThread.quitSafely()
        super.onDestroy()
    }
}
