package com.project.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.project.map.databinding.ActivityMapsBinding
import java.util.*


/*Google Map integration to Android Apps*/
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private var globalAddress: Address? = null
    private lateinit var markerOptions: MarkerOptions
    private lateinit var globalLatLng: LatLng
    private var currentLocationLatLng: LatLng? = null
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val requestPermissionCode = 1
    private lateinit var mapView: View
    private var zoom: Float = 15f
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.container.root.visibility = View.GONE
    }

    private fun checkAvailabilityOfPermission() {
        if (isPermissionGranted()) {
            onPermissionSuccess()
        } else {
            enableMyLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAvailabilityOfPermission()
    }

    /*
    * In this we will get last location first using fused location API.
    * Instantiate mapFragment as SupportFragmentManager to access its view and to load map async.
    * On Map Loading, callback method onMapReady() will be called.
    */

    private fun onPermissionSuccess() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapView = mapFragment.requireView()
        mapFragment.getMapAsync(this)
        setSupportActionBar(binding.toolbar)
    }

    @SuppressLint("MissingPermission")
    private fun setUpListeners() {
        /* Fused Api is used to get last location */
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                locateCoordinates(LatLng(location.latitude, location.longitude), zoom)
            } else {
                val indiaDelhi = LatLng(28.7041, 77.1025) // in case location is null, default location
                locateCoordinates(indiaDelhi, mMap.cameraPosition.zoom)
            }
        }

        /*Fused Api is used to get current location*/
        setUpCurrentLocationListener()

        /*Event handling on dragging of marker on GMap*/
        mMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDrag(p0: Marker) {
                binding.ltnContainer.ivLocation.setColorFilter(ContextCompat.getColor(this@MapsActivity, R.color.black))
            }

            override fun onMarkerDragEnd(marker: Marker) {
                locateCoordinates(marker.position, mMap.cameraPosition.zoom)
            }

            override fun onMarkerDragStart(p0: Marker) {
            }

        })

        mMap.setOnCameraIdleListener {
            val geocoder = Geocoder(this@MapsActivity)
            var listAddress: List<Address>? = null
            try {
                listAddress = geocoder.getFromLocation(globalLatLng.latitude, globalLatLng.longitude, 1)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            if (listAddress != null && listAddress.isNotEmpty()) {
                bindDataWithViews(listAddress[0])
                Log.d("setOnCameraIdleListener", "${listAddress[0]}")
            }
        }
        /*Geocoder is used for geocoding and reverse geocoding*/
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val location = binding.searchView.query.toString()
                try {
                    val listAddress = locateQueryAddress(location)
                    if (listAddress != null) {
                        val address = listAddress[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        Log.d("setOnQueryTextListener", "$address")
                        locateCoordinates(latLng, mMap.cameraPosition.zoom)
                        val radius = 1000
                        setUpCircle(latLng, radius.toDouble())
                    }
                } catch (exp: Exception) {
                    Toast.makeText(this@MapsActivity, "Location doesn't exist", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.searchView.isIconified = true
                    binding.searchView.onActionViewCollapsed()
                    binding.searchView.clearFocus()
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        /*custom relocate-me button*/
        binding.ltnContainer.root.setOnClickListener {
            getMyLocation()
        }

        binding.container.btnConfirm.setOnClickListener {
            if (globalAddress != null) {
                val detailedIntent = Intent(this, DetailedActivity::class.java)
                detailedIntent.putExtra("address", globalAddress)
                startActivity(detailedIntent)
            }

        }
    }

    fun locateQueryAddress(location: String): List<Address>? {
        var listAddress: List<Address>? = null
        if (location.isNotEmpty()) {
            val geocoder = Geocoder(this@MapsActivity, Locale.ENGLISH)
            try {
                listAddress = geocoder.getFromLocationName(location, 5)
            } catch (exp: Exception) {
                Toast.makeText(this@MapsActivity, "Location doesn't exist", Toast.LENGTH_SHORT).show()
            }
            return listAddress
        }
        return null
    }

    /*To get current location, Fused API is used */
    @SuppressLint("MissingPermission")
    private fun setUpCurrentLocationListener() {
        val locationRequest: LocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).setWaitForAccurateLocation(false)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLocationLatLng = LatLng(location.latitude, location.longitude)
                }
            }
        }, Looper.myLooper())
    }

    /*currentLocationLatLng is returned by fused lastLocation() method, and updated by currentLocation()  */
    private fun getMyLocation() {
        if (currentLocationLatLng != null) {
            locateCoordinates(currentLocationLatLng!!, 15f)
            this.zoom = 18f
            globalLatLng = currentLocationLatLng as LatLng
            binding.ltnContainer.ivLocation.setColorFilter(ContextCompat.getColor(this@MapsActivity, R.color.md_theme_light_primary))
        } else {
            Toast.makeText(this, "Please enable location from settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun locateCoordinates(location: LatLng, zoom: Float = this.zoom) {
        mMap.clear()
        globalLatLng = location
        markerOptions = MarkerOptions().position(location).draggable(true)
        mMap.addMarker(markerOptions)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(location))
        binding.ltnContainer.ivLocation.setColorFilter(ContextCompat.getColor(this@MapsActivity, R.color.black))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, zoom))
    }

    /*binding of data with views on getting location*/
    private fun bindDataWithViews(address: Address) {
        binding.container.root.visibility = View.VISIBLE
        globalAddress = address
        binding.container.apply {
            tvLocation.text = buildString {
                if (address.subAdminArea != null) {
                    append(address.subAdminArea)
                }
                if (address.adminArea != null) {
                    if (address.subAdminArea != null && address.adminArea != null) {
                        append(" , ")
                    }
                    append(address.adminArea)
                }
            }
            tvPreciseLocation.text = buildString {
                if (address.featureName != null) {
                    append(address.featureName)
                }
                if (address.countryName != null) {
                    if (address.featureName != null && address.countryName != null) {
                        append(" , ")
                    }
                    append(address.countryName)
                }
                if (address.postalCode != null) {
                    if (address.countryName != null && address.postalCode != null) {
                        append(" , ")
                    }
                    append(address.postalCode)
                }
            }
        }
    }

    /*Prompting user to enable location*/
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            mMap.isMyLocationEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                requestPermissionCode
            )
        }
    }

    fun setUpCircle(latLng: LatLng, radius: Double) {
        mMap.addCircle(
            CircleOptions().center(latLng).radius(radius).strokeColor(R.color.black).fillColor(R.color.md_theme_light_primary).strokeWidth(1f)
        )
    }

    /*onMapReady callback is callback method which is called when map is loaded successfully*/
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
        setUpListeners()  // added here because mMap object initialized here and only after that we can set up events on it
        manageDefaultRelocateButton()
        setMapLongClick()
        setPoiClick()
    }

    private fun manageDefaultRelocateButton() {
        if (mapView.findViewById<View>("1".toInt()) != null) {
            val locationButton = (mapView.findViewById<View>("1".toInt()).parent as View).findViewById<View>("2".toInt())
            locationButton.visibility = View.GONE
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.popup_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        // Change the map type based on the user's selection.
        R.id.normal_map -> {
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionCode) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                onPermissionSuccess()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPermissionGranted(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

    }


    private fun setMapLongClick() {
        mMap.setOnMapLongClickListener { latLangLocation ->
            locateCoordinates(latLangLocation, mMap.cameraPosition.zoom)
        }
    }

    /*Poi are public interest location, where people gathered*/
    private fun setPoiClick() {
        mMap.setOnPoiClickListener { poi ->
            val poiMarker = mMap.addMarker(
                MarkerOptions().position(poi.latLng)
                    .title(poi.name)
            )
            poiMarker?.showInfoWindow()
        }
    }

}