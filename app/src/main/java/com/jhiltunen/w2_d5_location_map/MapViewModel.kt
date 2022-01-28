package com.jhiltunen.w2_d5_location_map

import android.location.Address
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.util.GeoPoint
import java.io.IOException

class MapViewModel : ViewModel() {
    private var _mapData: MutableLiveData<MapData> = MutableLiveData()
    var mapData: LiveData<MapData> = _mapData
    var startPoint: GeoPoint = GeoPoint(60.166640739, 24.943536799)
    private var _address: MutableLiveData<String> = MutableLiveData()
    var address: LiveData<String> = _address

    init {
        // Helsinki 60.166640739°N, 24.943536799°E
        _mapData.postValue(MapData(startPoint, "Huuhaa"))
    //_mapData.postValue(address.value?.let { MapData(startPoint, it) })
    }

    fun updateLocation(newLocation: Location) {
        startPoint.longitude = newLocation.longitude
        startPoint.latitude = newLocation.latitude
        viewModelScope.launch(Dispatchers.IO) {
            _address.postValue(getAddress(GeoPoint(newLocation.latitude, newLocation.longitude)))
        }
    }

    fun getAddress(p: GeoPoint): String {
        val geocoder = GeocoderNominatim(BuildConfig.APPLICATION_ID)
        //GeocoderGraphHopper geocoder = new GeocoderGraphHopper(Locale.getDefault(), graphHopperApiKey);
        //GeocoderGraphHopper geocoder = new GeocoderGraphHopper(Locale.getDefault(), graphHopperApiKey);
        val theAddress: String?
        theAddress = try {
            val dLatitude = p.latitude
            val dLongitude = p.longitude
            val addresses: List<Address> = geocoder.getFromLocation(dLatitude, dLongitude, 1)
            val sb = StringBuilder()
            if (addresses.size > 0) {
                val address: Address = addresses[0]
                val n: Int = address.getMaxAddressLineIndex()
                for (i in 0..n) {
                    if (i != 0) sb.append(", ")
                    sb.append(address.getAddressLine(i))
                }
                sb.toString()
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
        return theAddress ?: ""
    }
}

data class MapData(val geoPoint: GeoPoint, val address: String)
