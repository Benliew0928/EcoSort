package com.example.ecosort

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.huawei.hms.api.HuaweiApiAvailability
import com.huawei.hms.maps.MapView as HMapView
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.MapsInitializer
import com.huawei.hms.maps.model.CameraPosition
import com.huawei.hms.maps.model.LatLng as HLatLng
import com.huawei.hms.maps.model.MarkerOptions as HMarkerOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView as GMapView
import com.google.android.gms.maps.OnMapReadyCallback as GOnMapReadyCallback
import com.google.android.gms.maps.MapsInitializer as GMapsInitializer
import com.google.android.gms.maps.model.CameraPosition as GCameraPosition
import com.google.android.gms.maps.model.LatLng as GLatLng
import com.google.android.gms.maps.model.MarkerOptions as GMarkerOptions

class MapActivity : AppCompatActivity(), GOnMapReadyCallback {

    private var hMapView: HMapView? = null
    private var gMapView: GMapView? = null
    private var huaweiMap: HuaweiMap? = null
    private var googleMap: GoogleMap? = null
    private lateinit var mapContainer: FrameLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val btnBackMap = findViewById<Button>(R.id.btnBackMap)
        val btnNav1 = findViewById<Button>(R.id.btnNav1)
        val btnNav2 = findViewById<Button>(R.id.btnNav2)
        mapContainer = findViewById(R.id.map_container)

        // Back button - return to main activity
        btnBackMap.setOnClickListener {
            finish() // This will go back to the previous activity (MainActivity)
        }

        // Initialize map with HMS first, fallback to GMS
        try {
            if (isHuaweiMobileServicesAvailable()) {
                initHmsMap(savedInstanceState)
            } else if (isGoogleMobileServicesAvailable()) {
                initGmsMap(savedInstanceState)
            } else {
                Toast.makeText(this, "Map unavailable on this device", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Map initialization failed", Toast.LENGTH_LONG).show()
        }

        // Navigation buttons for recycling stations
        btnNav1.setOnClickListener {
            navigateTo("Kuala Green Center")
        }

        btnNav2.setOnClickListener {
            navigateTo("Petaling Recycle Hub")
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        super.onBackPressed()
        finish() // Return to previous activity
    }

    private fun isHuaweiMobileServicesAvailable(): Boolean {
        val availability = HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(this)
        return availability == com.huawei.hms.api.ConnectionResult.SUCCESS
    }

    private fun isGoogleMobileServicesAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        return availability == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    private fun initHmsMap(savedInstanceState: Bundle?) {
        try {
            MapsInitializer.initialize(applicationContext)
            hMapView = HMapView(this)
            hMapView?.onCreate(savedInstanceState)
            mapContainer.addView(hMapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            hMapView?.getMapAsync { map ->
                huaweiMap = map
                val center = HLatLng(3.1390, 101.6869) // Kuala Lumpur
                map.moveCamera(com.huawei.hms.maps.CameraUpdateFactory.newCameraPosition(CameraPosition(center, 12f, 0f, 0f)))
                map.addMarker(HMarkerOptions().position(center).title("Nearby Recycling Stations"))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "HMS Map failed to initialize", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initGmsMap(savedInstanceState: Bundle?) {
        try {
            GMapsInitializer.initialize(applicationContext)
            gMapView = GMapView(this)
            gMapView?.onCreate(savedInstanceState)
            mapContainer.addView(gMapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            gMapView?.getMapAsync(this)
        } catch (_: Exception) {
            Toast.makeText(this, "Google Map failed to initialize", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val center = GLatLng(3.1390, 101.6869)
        map.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(GCameraPosition(center, 12f, 0f, 0f)))
        map.addMarker(GMarkerOptions().position(center).title("Nearby Recycling Stations"))
    }

    private fun hasGoogleMapsApiKey(): Boolean {
        return try {
            val ai: ApplicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val bundle = ai.metaData
            val key = bundle?.getString("com.google.android.geo.API_KEY")
            !key.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun navigateTo(query: String) {
        // Prefer HMS navigation if available (Petal Maps URI), else fallback to Google Maps
        if (isHuaweiMobileServicesAvailable()) {
            val petalUri = Uri.parse("petalmaps://poi?q=" + Uri.encode(query))
            val petalIntent = Intent(Intent.ACTION_VIEW, petalUri)
            if (petalIntent.resolveActivity(packageManager) != null) {
                startActivity(petalIntent)
                return
            }
        }
        val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Generic geo intent fallback
            val genericIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            if (genericIntent.resolveActivity(packageManager) != null) {
                startActivity(genericIntent)
            } else {
                Toast.makeText(this, "$query - navigation app not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hMapView?.onResume()
        gMapView?.onResume()
    }

    override fun onPause() {
        gMapView?.onPause()
        hMapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        gMapView?.onDestroy()
        hMapView?.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        gMapView?.onLowMemory()
        hMapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        gMapView?.onSaveInstanceState(outState)
        hMapView?.onSaveInstanceState(outState)
    }
}
