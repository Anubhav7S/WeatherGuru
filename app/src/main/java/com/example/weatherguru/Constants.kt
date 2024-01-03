package com.example.weatherguru

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants{
    const val APP_ID:String="b7486a7fdb5cf71551e5b393f51b4ae3" //4a9951826bbe75d38fff39b15228f272 //2bf248a8a5a6ba902c349b6a3f70fd33 //b7486a7fdb5cf71551e5b393f51b4ae3
    const val BASE_URL:String="http://api.openweathermap.org/data/"
    const val METRIC_UNIT:String="metric"
    const val PREFERENCE_NAME="WeatherGuruPreference"
    const val WEATHER_RESPONSE_DATA="weather_response_data"
    fun isNetworkAvailable(context: Context):Boolean{
        val connectivityManager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
//
//        }
        val network=connectivityManager.activeNetwork?:return false
        val activeNetwork=connectivityManager.getNetworkCapabilities(network)?:return false //return false if empty
        return when{
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->true //for those using LAN
            else->false //if we have none of the above 3
        }
    }
}