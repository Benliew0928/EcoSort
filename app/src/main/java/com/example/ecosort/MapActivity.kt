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
import com.google.android.material.bottomsheet.BottomSheetBehavior
// HILT AND COROUTINE IMPORTS
import com.example.ecosort.data.firebase.FirestoreService
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.widget.LinearLayout

import com.example.ecosort.hms.RecycleBinAdapter
import com.example.ecosort.hms.OnBinItemClickListener

import android.view.View

// MAP LISTENER IMPORTS
import com.huawei.hms.maps.HuaweiMap.OnMarkerClickListener
import com.huawei.hms.maps.model.Marker

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.location.Location

private var isAdmin: Boolean = false

/**
 * An Activity using Huawei Map Kit and Location Kit via the recommended SupportMapFragment approach.
 * This activity fetches bin locations from Firestore and displays them on the map.
 */
@AndroidEntryPoint
class MapActivity : AppCompatActivity() , OnMarkerClickListener, OnBinItemClickListener {

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
    private lateinit var behavior: BottomSheetBehavior<LinearLayout>

    private var recycleBinAdapter: RecycleBinAdapter? = null
    private var currentBinList: List<BinListItem> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarMap)


        val bottomSheet = findViewById<LinearLayout>(R.id.bottom_sheet)
        behavior = BottomSheetBehavior.from(bottomSheet) // ⭐ REMOVE 'val' here to initialize the class property ⭐
        behavior.isFitToContents = false


        behavior.state = BottomSheetBehavior.STATE_COLLAPSED


        val peekHeightInPixels = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
        behavior.peekHeight = peekHeightInPixels


        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // When the sheet is fully collapsed (only peek is visible), reset the padding.
                    huaweiMap?.setPadding(0, 0, 0, 0)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Not used for this feature, but required by the interface
            }
        })

        // 3. Set the state to hide if swiped down far enough
        behavior.isHideable = false

        val btnRefreshLocation = findViewById<Button>(R.id.btnRefreshLocation)

        btnRefreshLocation.setOnClickListener {
            // 1. Get a fresh location (This updates currentUserLatLng)
            getLastLocation()

            // 2. getLastLocation() will call fetchRecycleBins(), which re-sorts and reloads the map.
            Toast.makeText(this, "Refreshing location and list...", Toast.LENGTH_SHORT).show()
        }

        val btnAddStationPlaceholder = findViewById<Button>(R.id.btnAddStationPlaceholder)

        // Assign the original "Add Bin" functionality to the new "Add Station" button
        btnAddStationPlaceholder.setOnClickListener {
            // 1. Get the current center of the map view
            val centerLatLng = huaweiMap?.cameraPosition?.target

            // 2. Launch AddBinActivity (or AddStationActivity)
            val intent = Intent(this, AddBinActivity::class.java).apply {
                if (centerLatLng != null) {
                    // Pass the current map center location to the new activity
                    putExtra("EXTRA_LATITUDE", centerLatLng.latitude)
                    putExtra("EXTRA_LONGITUDE", centerLatLng.longitude)
                }
            }
            startActivity(intent)
        }

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
        toolbar.setNavigationOnClickListener {
            finish() }

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


    private fun getScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }



    // --- Marker Click Listener Implementation ---
    // MapActivity.kt

    // --- Marker Click Listener Implementation ---
    override fun onMarkerClick(marker: Marker): Boolean {
        huaweiMap?.let { map ->

            // 1. Map Centering and UI Setup
            val markerLatLng = marker.position
            val screenHeight = getScreenHeight()
            val paddingBottom = (screenHeight * 0.50).toInt()

            map.setPadding(0, 0, 0, paddingBottom)
            val zoomLevel = map.cameraPosition?.zoom ?: 15f
            val cameraUpdate = com.huawei.hms.maps.CameraUpdateFactory.newLatLngZoom(markerLatLng, zoomLevel)

            map.animateCamera(cameraUpdate, 500, null)
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

            // 2. Synchronization and Sorting Logic
            val photoUrl = markerDataMap[marker.id]

            // Find the actual BinListItem object that corresponds to the clicked marker
            val clickedBin = currentBinList.find {
                it.name == marker.title && it.photoUrl == photoUrl
            }

            if (clickedBin != null) {

                // a. Create a new list structure by moving the clicked item to index 0
                val mutableList = currentBinList.toMutableList()
                mutableList.remove(clickedBin)
                mutableList.add(0, clickedBin) // Insert at the top

                // b. Update the global list state
                currentBinList = mutableList.toList()
                val newSelectedIndex = 0 // The clicked item is now at index 0

                // c. Update the adapter with the new, sorted list and set the selection
                recycleBinAdapter?.setSelected(RecyclerView.NO_POSITION) // Clear old highlight first
                recycleBinAdapter?.updateList(currentBinList, newSelectedIndex) // **Requires the updateList function**

                // d. SCROLL the top item (the one we just moved) into view
                rvNearbyStations.scrollToPosition(0)
            }

            Toast.makeText(this, "Focusing and sorting on: ${marker.title}", Toast.LENGTH_SHORT).show()
        }

        // Return true to indicate that we have consumed the event
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
                                // Apply small random offset for visualization (to prevent stacking)
                                val offsetLat = lat + Math.random() * 0.0001
                                val offsetLng = lng + Math.random() * 0.0001
                                val displayLatLng = LatLng(offsetLat, offsetLng)
                                val actualLatLng = LatLng(lat, lng)

                                val statusText = if (isVerified) "Status: Verified" else "Status: Pending Verification"
                                map.addMarker(
                                    MarkerOptions().position(displayLatLng).title(name).snippet(statusText)
                                )?.let { markerDataMap[it.id] = photoUrl }

                                // Calculate real distance
                                val distance = if (userLocation != null) {
                                    calculateDistance(userLocation, actualLatLng)
                                } else {
                                    "📍 Location Unknown"
                                }

                                // Update the List Data Model
                                listItems.add(BinListItem(
                                    name, distance, isVerified, photoUrl, lat, lng
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing bin document: ${e.message}")
                        }
                    }
                }

                // ⭐ CRITICAL STEP 1: Sort the listItems by distance ⭐
                val sortedList = listItems.sortedWith(compareBy {
                    // Extract the numerical distance from the string for reliable comparison
                    // e.g., converts "📍 2.5 km away" into the number 2.5
                    it.distance.split(" ")[1].toDoubleOrNull() ?: Double.MAX_VALUE
                })

                // 2. Store the sorted list
                currentBinList = sortedList.toList()

                // 3. Initialize and save the adapter reference
                recycleBinAdapter = RecycleBinAdapter(currentBinList, this@MapActivity, isAdmin) // ⭐ PASS isAdmin ⭐
                rvNearbyStations.adapter = recycleBinAdapter

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


    // MapActivity.kt

    override fun onBinItemClick(latitude: Double, longitude: Double, photoUrl: String, clickedIndex: Int) {
        huaweiMap?.let { map ->
            val markerLatLng = LatLng(latitude, longitude)

            // 1. Map Centering and UI Setup (reusing the same logic as onMarkerClick)
            val screenHeight = getScreenHeight()
            val paddingBottom = (screenHeight * 0.50).toInt()
            map.setPadding(0, 0, 0, paddingBottom)
            val zoomLevel = map.cameraPosition?.zoom ?: 15f
            val cameraUpdate = com.huawei.hms.maps.CameraUpdateFactory.newLatLngZoom(markerLatLng, zoomLevel)

            map.animateCamera(cameraUpdate, 500, null)
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

            // 2. Synchronization and Sorting Logic

            // Find the actual BinListItem object at the clicked index
            val clickedBin = currentBinList.getOrNull(clickedIndex)

            if (clickedBin != null) {

                // a. Create a new list structure by moving the clicked item to index 0
                val mutableList = currentBinList.toMutableList()
                mutableList.remove(clickedBin)
                mutableList.add(0, clickedBin) // Insert at the top

                // b. Update the global list state
                currentBinList = mutableList.toList()
                val newSelectedIndex = 0 // The clicked item is now at index 0

                // c. Update the adapter with the new, sorted list and set the selection
                recycleBinAdapter?.setSelected(RecyclerView.NO_POSITION) // Clear old highlight
                recycleBinAdapter?.updateList(currentBinList, newSelectedIndex)

                // d. SCROLL the top item (the one we just moved) into view
                rvNearbyStations.scrollToPosition(0)
            }

            Toast.makeText(this, "Focusing and sorting list.", Toast.LENGTH_SHORT).show()
        }
    }
}