package com.example.ecosort.hms

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.SupportMapFragment
import com.huawei.hms.maps.model.CameraPosition
import com.huawei.hms.maps.model.LatLng
import com.huawei.hms.maps.model.MarkerOptions
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationServices
import com.example.ecosort.R
import android.content.Intent

// HILT AND COROUTINE IMPORTS
import com.example.ecosort.data.firebase.FirestoreService
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// MAP LISTENER IMPORTS
import com.huawei.hms.maps.HuaweiMap.OnMarkerClickListener
import com.huawei.hms.maps.model.Marker

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.location.Location

/**
 * An Activity using Huawei Map Kit and Location Kit via the recommended SupportMapFragment approach.
 * This activity fetches bin locations from Firestore and displays them on the map.
 */
@AndroidEntryPoint
class MapActivity : AppCompatActivity() , OnMarkerClickListener {

    private val TAG = "MapActivity"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var huaweiMap: HuaweiMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var firestoreService: FirestoreService

    private val markerDataMap = mutableMapOf<String, String>()
    private lateinit var rvNearbyStations: RecyclerView

    // NEW: Property to hold the user's location for distance calculation
    private var currentUserLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val btnBackMap = findViewById<Button>(R.id.btnBackMap)
        val btnAddBin = findViewById<Button>(R.id.btnAddBin)

        rvNearbyStations = findViewById(R.id.rvNearbyStations)
        rvNearbyStations.layoutManager = LinearLayoutManager(this)

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- Map Initialization using SupportMapFragment ---
        try {
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.hms_map_fragment) as? SupportMapFragment

            if (mapFragment == null) {
                Log.e(TAG, "FATAL ERROR: hms_map_fragment NOT FOUND.")
                Toast.makeText(this, "Map component missing!", Toast.LENGTH_LONG).show()
                return
            }

            mapFragment.getMapAsync { map ->
                huaweiMap = map
                Log.d(TAG, "Map loaded successfully via Fragment.")
                setupMap()
                checkLocationPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HMS Map initialization failed: ${e.message}")
            Toast.makeText(this, "Map unavailable on this device", Toast.LENGTH_LONG).show()
        }
        // --- End Map Initialization ---


        // Button functionality
        btnBackMap.setOnClickListener { finish() }
        btnAddBin.setOnClickListener {
            val centerLatLng = huaweiMap?.cameraPosition?.target

            // 2. Launch AddBinActivity
            val intent = Intent(this, AddBinActivity::class.java).apply {
                if (centerLatLng != null) {
                    // Pass the current map center location to the new activity
                    putExtra("EXTRA_LATITUDE", centerLatLng.latitude)
                    putExtra("EXTRA_LONGITUDE", centerLatLng.longitude)
                }
            }
            startActivity(intent)
        }
    }

    // FETCH DATA EVERY TIME THE SCREEN IS RESUMED
    override fun onResume() {
        super.onResume()
        // Refresh markers every time the user returns to this screen
        getLastLocation()
    }

    // --- Map Setup Functions ---
    private fun setupMap() {
        huaweiMap?.let { map ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
                getLastLocation()
            }

            // Initial fetch of data when the map is first set up
            fetchRecycleBins()

            // Move camera
            val initialLocation = LatLng(3.1390, 101.6869)
            map.moveCamera(com.huawei.hms.maps.CameraUpdateFactory.newCameraPosition(
                CameraPosition(initialLocation, 12f, 0f, 0f)
            ))

            // Set the marker click listener
            map.setOnMarkerClickListener(this)

            map.setOnMapLongClickListener { latLng ->
                Toast.makeText(this, "Pin dropped at: ${latLng.latitude}, ${latLng.longitude}. Ready for submission!", Toast.LENGTH_LONG).show()
            }
        }
    }



    // --- Marker Click Listener Implementation ---
    override fun onMarkerClick(marker: Marker): Boolean {
        // Retrieve the photo URL using the marker's ID
        val photoUrl = markerDataMap[marker.id]

        if (!photoUrl.isNullOrEmpty()) {
            // Launch the detail activity, passing the URL
            val intent = Intent(this, BinDetailActivity::class.java).apply {
                putExtra("EXTRA_PHOTO_URL", photoUrl)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Photo URL not found for this bin.", Toast.LENGTH_SHORT).show()
        }

        // Return true to indicate that we have consumed the event (no default info window opens)
        return true
    }

    // --- FUNCTION TO FETCH BINS FROM FIRESTORE (Uses stored location) ---
    private fun fetchRecycleBins() {
        huaweiMap?.clear()
        markerDataMap.clear()

        val listItems = mutableListOf<BinListItem>()
        val userLocation = currentUserLatLng // Access location property here

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                firestoreService.getRecycleBins()
            }

            result.onSuccess { binList ->
                huaweiMap?.let { map ->
                    binList.forEach { document ->
                        try {
                            val name = document["name"] as? String ?: "Unknown Bin"
                            val lat = document["latitude"] as? Double ?: 0.0
                            val lng = document["longitude"] as? Double ?: 0.0
                            val isVerified = document["isVerified"] as? Boolean ?: false
                            val photoUrl = document["photoUrl"] as? String ?: ""

                            if (lat != 0.0 && lng != 0.0) {
                                val binLatLng = LatLng(lat, lng)
                                val statusText = if (isVerified) "Status: Verified" else "Status: Pending Verification"

                                val marker = map.addMarker(
                                    MarkerOptions().position(binLatLng).title(name).snippet(statusText)
                                )
                                marker?.let { markerDataMap[it.id] = photoUrl }

                                // Calculate real distance or use placeholder
                                val distance = if (userLocation != null) {
                                    calculateDistance(userLocation, binLatLng)
                                } else {
                                    "📍 Location Unknown"
                                }

                                // Update the List Data Model
                                listItems.add(BinListItem(name, distance, isVerified, photoUrl))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing bin document: ${e.message}")
                        }
                    }

                    // Update the RecyclerView
                    rvNearbyStations.adapter = RecycleBinAdapter(listItems)
                }
            }.onFailure { exception ->
                Log.e(TAG, "Failed to fetch bins from Firestore: ", exception)
                Toast.makeText(this@MapActivity, "Failed to load bin locations.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Location Kit and Permission Functions (Unchanged) ---
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
            } else {
                Toast.makeText(this, "Location permission denied. Cannot show user location.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)

                    // STORE LOCATION HERE
                    currentUserLatLng = userLatLng

                    Log.d(TAG, "Last Location: ${userLatLng.latitude}, ${userLatLng.longitude}")
                    huaweiMap?.animateCamera(com.huawei.hms.maps.CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                } else {
                    Log.w(TAG, "Last location is null.")
                }
                // Always call fetchRecycleBins() after attempting to get location,
                // to update the map/list with the best available data.
                fetchRecycleBins()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to get last location: ${e.message}")
                fetchRecycleBins() // Fetch bins even if location failed
            }
        } else {
            // If permission is revoked, fetch bins without location data
            fetchRecycleBins()
        }
    }



    // --- DISTANCE CALCULATION FUNCTION (FIXED) ---
    private fun calculateDistance(userLocation: LatLng, binLocation: LatLng): String {
        // Uses Android's standard Location utility for accurate distance calculation in meters
        val userLoc = Location("user").apply {
            latitude = userLocation.latitude
            longitude = userLocation.longitude
        }
        val binLoc = Location("bin").apply {
            latitude = binLocation.latitude
            longitude = binLocation.longitude
        }

        val distanceInMeters = userLoc.distanceTo(binLoc)

        return if (distanceInMeters >= 1000) {
            // Convert to kilometers (e.g., 2.5 km)
            "📍 %.1f km away".format(distanceInMeters / 1000)
        } else {
            // Show in meters (e.g., 850 m)
            "📍 %d m away".format(distanceInMeters.toInt())
        }
    }
}