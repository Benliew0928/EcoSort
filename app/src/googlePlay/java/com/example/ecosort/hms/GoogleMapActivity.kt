package com.example.ecosort.hms

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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

import android.view.View

// MAP LISTENER IMPORTS
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener
import com.google.android.gms.maps.model.Marker

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.location.Location

/**
 * An Activity using Google Maps SDK and Location Services.
 * This activity fetches bin locations from Firestore and displays them on the map.
 * 
 * IMPORTANT: This is the GMS (Google Mobile Services) implementation.
 * For Huawei Maps implementation, see HuaweiMapActivity.kt
 * 
 * Features:
 * - Real-time location tracking
 * - Display recycling bin markers from Firebase
 * - Calculate and display distances to bins
 * - Bottom sheet with nearby stations list
 * - Add new recycling stations
 * - Synchronized marker/list selection
 */
@AndroidEntryPoint
class GoogleMapActivity : AppCompatActivity(), OnMarkerClickListener, OnBinItemClickListener {

    private val TAG = "GoogleMapActivity"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var firestoreService: FirestoreService

    private val markerDataMap = mutableMapOf<String, String>()
    private lateinit var rvNearbyStations: RecyclerView

    // Property to hold the user's location for distance calculation
    private var currentUserLatLng: LatLng? = null
    private lateinit var behavior: BottomSheetBehavior<LinearLayout>

    private var recycleBinAdapter: RecycleBinAdapter? = null
    private var currentBinList: List<BinListItem> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_map)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarMap)

        val bottomSheet = findViewById<LinearLayout>(R.id.bottom_sheet)
        behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = false

        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        val peekHeightInPixels = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
        behavior.peekHeight = peekHeightInPixels

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    // When the sheet is fully collapsed (only peek is visible), reset the padding.
                    googleMap?.setPadding(0, 0, 0, 0)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Not used for this feature, but required by the interface
            }
        })

        behavior.isHideable = false

        val btnRefreshLocation = findViewById<Button>(R.id.btnRefreshLocation)

        btnRefreshLocation.setOnClickListener {
            // Get a fresh location (This updates currentUserLatLng)
            getLastLocation()
            Toast.makeText(this, "Refreshing location and list...", Toast.LENGTH_SHORT).show()
        }

        val btnAddStationPlaceholder = findViewById<Button>(R.id.btnAddStationPlaceholder)

        // Assign the original "Add Bin" functionality to the new "Add Station" button
        btnAddStationPlaceholder.setOnClickListener {
            // Get the current center of the map view
            val centerLatLng = googleMap?.cameraPosition?.target

            // Launch AddBinActivity
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

        // Initialize Location Client (Google Play Services)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- Map Initialization using SupportMapFragment ---
        try {
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.google_map_fragment) as? SupportMapFragment

            if (mapFragment == null) {
                Log.e(TAG, "FATAL ERROR: google_map_fragment NOT FOUND.")
                Toast.makeText(this, "Map component missing!", Toast.LENGTH_LONG).show()
                return
            }

            mapFragment.getMapAsync { map ->
                googleMap = map
                Log.d(TAG, "✅ Google Map loaded successfully via Fragment.")
                setupMap()
                checkLocationPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Google Map initialization failed: ${e.message}")
            Toast.makeText(this, "Map unavailable on this device", Toast.LENGTH_LONG).show()
        }
        // --- End Map Initialization ---

        // Toolbar back button
        toolbar.setNavigationOnClickListener {
            finish()
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
        googleMap?.let { map ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
                getLastLocation()
            }

            // Initial fetch of data when the map is first set up
            fetchRecycleBins()

            // Move camera to initial location (Kuala Lumpur, Malaysia)
            val initialLocation = LatLng(3.1390, 101.6869)
            map.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
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
    override fun onMarkerClick(marker: Marker): Boolean {
        googleMap?.let { map ->

            // 1. Map Centering and UI Setup
            val markerLatLng = marker.position
            val screenHeight = getScreenHeight()
            val paddingBottom = (screenHeight * 0.50).toInt()

            map.setPadding(0, 0, 0, paddingBottom)
            val zoomLevel = map.cameraPosition?.zoom ?: 15f
            val cameraUpdate = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(markerLatLng, zoomLevel)

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
                recycleBinAdapter?.updateList(currentBinList, newSelectedIndex)

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
        googleMap?.clear()
        markerDataMap.clear()

        val listItems = mutableListOf<BinListItem>()
        val userLocation = currentUserLatLng // Access location property here

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                firestoreService.getRecycleBins()
            }

            result.onSuccess { binList ->
                googleMap?.let { map ->
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

                // Sort the listItems by distance
                val sortedList = listItems.sortedWith(compareBy {
                    // Extract the numerical distance from the string for reliable comparison
                    it.distance.split(" ")[1].toDoubleOrNull() ?: Double.MAX_VALUE
                })

                // Store the sorted list
                currentBinList = sortedList.toList()

                // Initialize and save the adapter reference
                val isAdmin = false // You can set this from session if needed
                recycleBinAdapter = RecycleBinAdapter(currentBinList, this@GoogleMapActivity, isAdmin)
                rvNearbyStations.adapter = recycleBinAdapter

            }.onFailure { exception ->
                Log.e(TAG, "Failed to fetch bins from Firestore: ", exception)
                Toast.makeText(this@GoogleMapActivity, "Failed to load bin locations.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Location and Permission Functions ---
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
                    googleMap?.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                } else {
                    Log.w(TAG, "Last location is null.")
                }
                // Always call fetchRecycleBins() after attempting to get location
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

    // --- DISTANCE CALCULATION FUNCTION ---
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
            // Convert to kilometers
            "📍 %.1f km away".format(distanceInMeters / 1000)
        } else {
            // Show in meters
            "📍 %d m away".format(distanceInMeters.toInt())
        }
    }

    // --- RecyclerView Item Click Implementation ---
    override fun onBinItemClick(latitude: Double, longitude: Double, photoUrl: String, clickedIndex: Int) {
        googleMap?.let { map ->
            val markerLatLng = LatLng(latitude, longitude)

            // 1. Map Centering and UI Setup (reusing the same logic as onMarkerClick)
            val screenHeight = getScreenHeight()
            val paddingBottom = (screenHeight * 0.50).toInt()
            map.setPadding(0, 0, 0, paddingBottom)
            val zoomLevel = map.cameraPosition?.zoom ?: 15f
            val cameraUpdate = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(markerLatLng, zoomLevel)

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

