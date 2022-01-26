package com.jhiltunen.w2_d5_location_map

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.osmdroid.api.IMapController
import org.osmdroid.util.GeoPoint

class MapViewModel : ViewModel() {
    private var _mapData: MutableLiveData<MapData> = MutableLiveData()
    var mapData: LiveData<MapData> = _mapData
    var startPoint: GeoPoint = GeoPoint(60.166640739, 24.943536799)

    init {
        // Helsinki 60.166640739°N, 24.943536799°E
        _mapData.value = MapData(startPoint, "Default")
    }

    fun updateLocation(newLocation: Location) {
        startPoint.longitude = newLocation.longitude
        startPoint.latitude = newLocation.latitude
        //mapController.setCenter(startPoint)
    }
}

data class MapData(val geoPoint: GeoPoint, val address: String)
