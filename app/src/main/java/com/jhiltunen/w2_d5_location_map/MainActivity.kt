package com.jhiltunen.w2_d5_location_map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import org.osmdroid.views.overlay.MinimapOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class MainActivity : ComponentActivity() {
    private lateinit var locationHandler: LocationHandler
    private lateinit var lastKnownLocation: State<Location?>
    private lateinit var mapViewModel: MapViewModel

    @ExperimentalFoundationApi
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
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                0
            )
        }

        // https://stackoverflow.com/questions/60608101/how-request-permissions-with-jetpack-compose?rq=1


        locationHandler.getMyLocation()
        setContent {
            lastKnownLocation = locationHandler.currentLocation.observeAsState()
            W2_D5_Location_MapTheme {
                Column(Modifier.padding(5.dp)) {
                    Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            locationHandler.startLocationUpdates()
                        }, modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2f)) {
                            Text(text = "Start tracking", textAlign = TextAlign.Center)
                        }
                        Button(onClick = {
                            locationHandler.stopLocationUpdates()
                        }, modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2f)) {
                            Text(text = "Stop tracking", textAlign = TextAlign.Center)
                        }
                        Button(onClick = {
                            locationHandler.getMyLocation()
                        }, modifier = Modifier
                            .weight(1f)
                            .aspectRatio(2f)) {
                            Text(text = "Set my location", textAlign = TextAlign.Center)
                        }
                    }
                    ShowMap(mapViewModel = mapViewModel, locationHandler = locationHandler,this@MainActivity)
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

@ExperimentalFoundationApi
@Composable
fun Location(locationHandler: LocationHandler) {
    val currentLocation = locationHandler.currentLocation.observeAsState()
    val lastKnownLocation = locationHandler.lastKnownLocation.observeAsState()
    val currentSpeed = locationHandler.currentSpeed.observeAsState()
    var topSpeed = locationHandler.topSpeed.observeAsState()
    val totalWalkedDistance = locationHandler.totalWalkedDistance.observeAsState()


    val data = listOf(
        "Current location\nLat: ${currentLocation.value?.latitude}\nLon: ${currentLocation.value?.latitude}",
        "Last known location\nLat: ${lastKnownLocation.value?.latitude}\nLon: ${lastKnownLocation.value?.longitude}",
        "Current speed\n${currentSpeed.value} km/h",
        "Total walked distance\n${if (totalWalkedDistance.value!! < 1000) totalWalkedDistance.value else totalWalkedDistance.value?.div(1000f)}"
    )
    Row {
        Card(
            modifier = Modifier.padding(4.dp).fillMaxWidth(),
            backgroundColor = Color.LightGray
        ) {
            Text(
                text = "Top speed: ${topSpeed.value} km/h",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(15.dp)
            )
        }
    }
    LazyVerticalGrid(
        cells = GridCells.Fixed(2),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(data.size) { index ->
            Card(
                modifier = Modifier.padding(4.dp),
                backgroundColor = Color.LightGray
            ) {
                Text(
                    text = data[index],
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(15.dp)
                )
            }
        }
    }
}

@Composable
fun composeMap(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = R.id.map
        }
    }
    return mapView
}

@ExperimentalFoundationApi
@Composable
fun ShowMap(mapViewModel: MapViewModel, locationHandler: LocationHandler, context: Context) {
    val map = composeMap()
    // hard coded zoom level and map center only at start
    var mapInitialized by remember(map) { mutableStateOf(false) }
    if (!mapInitialized) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.controller.setZoom(18.5)
        mapInitialized = true
        map.controller.setCenter(GeoPoint(60.166640739, 24.943536799))
    }
    // observer (e.g. update from the location change listener)
    val address by mapViewModel.mapData.observeAsState()
    val homeAddress by mapViewModel.address.observeAsState()
    var centerToUser by remember { mutableStateOf(true) }
    val marker = Marker(map)
    FloatingActionButton(modifier = Modifier
        .zIndex(100f)
        .fillMaxWidth(), onClick = {
        centerToUser = !centerToUser
    }) {
        if (centerToUser) {
            Row(Modifier.padding(15.dp)) {
                Text(text = "Disable center to my location")
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_my_location_24),
                    contentDescription = "Center to my location"
                )
            }
        } else {
            Row(Modifier.padding(15.dp)) {
                Text(text = "Enable center to my location")
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_location_searching_24),
                    contentDescription = "Center to my location disabled"
                )
            }
        }
    }
    Location(locationHandler = locationHandler)
    AndroidView({ map }) {
        address ?: return@AndroidView

        val mCompassOverlay =
            CompassOverlay(context, InternalCompassOrientationProvider(context), map)
        mCompassOverlay.enableCompass()

        val rotationGestureOverlay = RotationGestureOverlay(map)
        map.setMultiTouchControls(true)
        rotationGestureOverlay.isEnabled = true
        rotationGestureOverlay.isOptionsMenuEnabled = true

        val dm: DisplayMetrics = context.resources.displayMetrics
        val scaleBarOverlay = ScaleBarOverlay(map)
        scaleBarOverlay.setCentred(true)
        //play around with these values to get the location on screen in the right place for your application
        scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 20)

        if (centerToUser) {
            it.controller.setCenter(address?.geoPoint)
        }


        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.position = address?.geoPoint
        marker.closeInfoWindow()
        marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_person_pin_circle_24);
        marker.title = homeAddress + "\nLat: ${address?.geoPoint?.latitude}, Long: ${address?.geoPoint?.longitude}"
        map.overlays.add(marker)
        map.overlays.add(mCompassOverlay)
        map.overlays.add(rotationGestureOverlay)
        map.overlays.add(scaleBarOverlay)
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

    private var _topSpeed: MutableLiveData<Float> = MutableLiveData(0f)
    var topSpeed: LiveData<Float> = _topSpeed

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
                Log.d("LOCATIONCALLBACK", "new lat: ${locationResult.lastLocation.latitude} and long: ${locationResult.lastLocation.longitude}")
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
                _currentSpeed.postValue(locationResult.lastLocation.speed * 3.6f)
                if (topSpeed.value!! < _currentSpeed.value!!) {
                    _topSpeed.postValue(_currentSpeed.value)
                }
            }
        }
    }

    fun startLocationUpdates() {
        Log.d("START", "START L??OCATION UPDATES")
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