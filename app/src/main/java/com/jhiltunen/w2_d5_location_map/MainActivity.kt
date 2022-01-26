package com.jhiltunen.w2_d5_location_map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.jhiltunen.w2_d5_location_map.ui.theme.W2_D5_Location_MapTheme
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {
    private lateinit var locationHandler: LocationHandler
    private lateinit var lastKnownLocation: State<Location?>
    private lateinit var mapViewModel: MapViewModel
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //important! set your user agent to prevent getting banned from the osm servers
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID

        mapViewModel = MapViewModel()
        locationHandler = LocationHandler(context = applicationContext, mapViewModel = mapViewModel)

        if ((Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                0
            )
        }

        // https://stackoverflow.com/questions/60608101/how-request-permissions-with-jetpack-compose?rq=1


        locationHandler.getMyLocation()
        setContent {
            lastKnownLocation = locationHandler.currentLocation.observeAsState()
            W2_D5_Location_MapTheme {
                Column {
                    Text(text = "Current location lat: ${lastKnownLocation.value?.latitude} and long ${lastKnownLocation.value?.longitude}")
                    Button(onClick = {
                        locationHandler.startLocationUpdates()
                    }) {
                        Text(text = "Start tracking")
                    }
                    Button(onClick = { locationHandler.getMyLocation() }) {
                        Text(text = "Set my location")
                    }
                    Location(locationHandler = locationHandler)
                    ShowMap(mapViewModel = mapViewModel, this@MainActivity)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        locationHandler.stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        locationHandler.startLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        locationHandler.stopLocationUpdates()
    }
}

@Composable
fun Location(locationHandler: LocationHandler) {
    var currentLocation = locationHandler.currentLocation.observeAsState()
    var lastKnownLocation = locationHandler.lastKnownLocation.observeAsState()
    var currentSpeed = locationHandler.currentSpeed.observeAsState()
    var topSpeed = locationHandler.topSpeed.observeAsState()
    var totalWalkedDistance = locationHandler.totalWalkedDistance.observeAsState()


    Text("Current location: ${currentLocation.value?.latitude}")
    Text("LastKnown location: ${lastKnownLocation.value?.latitude}")
    Text("Current speed: ${currentSpeed.value}")
    Text("Total walked distance: ${totalWalkedDistance.value}")
}

@Composable
fun ComposeMap(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = R.id.map
        }
    }
    return mapView
}

@Composable
fun ShowMap(mapViewModel: MapViewModel, context: Context) {
    val map = ComposeMap()
    // hard coded zoom level and map center only at start
    var mapInitialized by remember(map) { mutableStateOf(false) }
    if (!mapInitialized) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(18.5)
        mapInitialized = true
        map.setMultiTouchControls(true)
        map.controller.setCenter(GeoPoint(60.166640739, 24.943536799))
    }
    // observer (e.g. update from the location change listener)
    val address by mapViewModel.mapData.observeAsState()
    val marker = Marker(map)
    AndroidView({ map }) {
        address ?: return@AndroidView

        val mCompassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), map)
        mCompassOverlay.enableCompass()

        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), map);
        myLocationOverlay.enableMyLocation();


        it.controller.setCenter(address?.geoPoint)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.position = address?.geoPoint
        //marker.closeInfoWindow()
        marker!!.icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_person_pin_circle_24);
        marker.title = address?.address
        marker.showInfoWindow()
        //map.overlays.add(marker)
        map.overlays.add(myLocationOverlay)
        map.overlays.add(mCompassOverlay)
        map.invalidate()
    }
}

class LocationHandler(private var context: Context, var mapViewModel: MapViewModel) {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var _currentLocation: MutableLiveData<Location> = MutableLiveData()
    var currentLocation: LiveData<Location> = _currentLocation

    private var _lastKnownLocation: MutableLiveData<Location> = MutableLiveData()
    var lastKnownLocation: LiveData<Location> = _lastKnownLocation

    private var _currentSpeed: MutableLiveData<Float> = MutableLiveData(0f)
    var currentSpeed: LiveData<Float> = _currentSpeed

    private var _topSpeed: MutableLiveData<Location> = MutableLiveData()
    var topSpeed: LiveData<Location> = _topSpeed

    private var _totalWalkedDistance: MutableLiveData<Float> = MutableLiveData(0f)
    var totalWalkedDistance: LiveData<Float> = _totalWalkedDistance

    fun getMyLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                _lastKnownLocation.postValue(it)
                _currentLocation.postValue(it)
                mapViewModel.updateLocation(it)
                Log.d(
                    "GEOLOCATION",
                    "last location latitude: ${it?.latitude} and longitude: ${it?.longitude}"
                )
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                if (_lastKnownLocation.value != null) {
                    _totalWalkedDistance.postValue(
                        _totalWalkedDistance.value?.plus(
                            _lastKnownLocation.value!!.distanceTo(locationResult.lastLocation)
                        )
                    )
                }

                //_lastKnownLocation.postValue(locationResult.lastLocation)
                currentLocation.value?.let { mapViewModel.updateLocation(it) }
                Log.d("LOCATIONCALLBACK", "${locationResult.locations.size}")
                /*for (location in locationResult.locations) {
                    Log.d("DISTANCE", "distance: $distance prev: $prev")
                    if (prev != null) {
                        distance += location.distanceTo(prev)
                    }
                    prev = location
                    //
                    Log.d("GEOLOCATION", "new location latitude: ${ location.latitude } and longitude: ${ location.longitude } distance $distance" )
                }*/
                _currentLocation.postValue(locationResult.lastLocation)
                _currentSpeed.postValue(locationResult.lastLocation.speed)
            }
        }
    }

    fun startLocationUpdates() {
        val locationRequest = LocationRequest
            .create()
            .setInterval(1000)
            .setPriority(PRIORITY_HIGH_ACCURACY)
        //if permissions granted...
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}