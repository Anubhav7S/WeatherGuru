package com.example.weatherguru

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.weatherguru.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var binding:ActivityMainBinding?=null
    private lateinit var mFusedLocationClient:FusedLocationProviderClient
    private lateinit var mSharedPreferences: SharedPreferences
    private var mLatitude:Double=0.0
    private var mLongitude:Double=0.0
    private var mProgressDialog:Dialog?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        mFusedLocationClient=LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)
        setupUI()
        if (!isLocationEnabled()){
            Toast.makeText(this,"Location is turned off. Please turn it on!",Toast.LENGTH_SHORT).show()
            val intent= Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else{
            Dexter.withContext(this).withPermissions(android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION).withListener(object :MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report != null) {
                        if (report.areAllPermissionsGranted()){
                            requestNewLocationData()
                        }

                    }
                    if (report!!.isAnyPermissionPermanentlyDenied){
                        Toast.makeText(this@MainActivity,"Location permission has been denied.",Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    showRationaleDialogForPermissions()
                }

            }).onSameThread().check()
        }
    }
    private fun isLocationEnabled():Boolean{
        val locationManager: LocationManager =getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER)
    }
    @SuppressLint("missingPermission")
    private fun requestNewLocationData(){
        var mLocationRequest = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateDistanceMeters(0F)
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
            setMaxUpdates(1)
        }.build()

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,mLocationCallback, Looper.myLooper())

    }

    private val mLocationCallback=object : LocationCallback(){        //to get location of the user
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            mLatitude= mLastLocation!!.latitude
            mLongitude = mLastLocation.longitude
            getLocationWeatherDetails(mLatitude,mLongitude)
            mFusedLocationClient.removeLocationUpdates(this)
        }
    }


    private fun showRationaleDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("Permissions to use the feature have been denied. " +
                "They can be granted in Application Settings.").setPositiveButton("GO TO SETTINGS"){
                _,_ ->
            try {
                val intent=Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri= Uri.fromParts("package",packageName,null)
                intent.data=uri
                startActivity(intent)
            }catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }.setNegativeButton("Cancel"){dialog,which->
            dialog.dismiss() }.show()
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit:Retrofit=Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val service:WeatherService=retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall:Call<WeatherResponse> = service.getWeather(latitude,longitude, Constants.METRIC_UNIT,Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        val weatherResponseJsonString=Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit() //default code for shared preferences
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString) // if first is string then second also must be string
                        editor.apply()

                        setupUI()
                       // setupUI(weatherList!!)
                        Toast.makeText(this@MainActivity,"Function Works!",Toast.LENGTH_SHORT).show()
                    }
                    else{
                        val rc=response.code()
                        when(rc){
                            400->{
                                Toast.makeText(this@MainActivity,"Error:400",Toast.LENGTH_LONG).show()
                            }
                            404->{
                                Toast.makeText(this@MainActivity,"Error:404",Toast.LENGTH_LONG).show()
                            }
                            else->{
                                Toast.makeText(this@MainActivity,"Error is Generic",Toast.LENGTH_LONG).show()
                            }


                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity,t.message.toString(),Toast.LENGTH_LONG).show()
                    hideCustomProgressDialog()
                }

            })
        }
        else{
            Toast.makeText(this,"FAILURE",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog= Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {  //inflating the view with the refresh button
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.refresh->{
                requestNewLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    }

    private fun hideCustomProgressDialog(){
        if (mProgressDialog!=null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(){
        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"") //get value from shared preferences
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList=Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
            for (i in weatherList.weather.indices){
                binding?.tvMain?.text=weatherList.weather[i].main
                binding?.tvMainDesc?.text=weatherList.weather[i].description
                binding?.tvTemp?.text= weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding?.tvSunriseTime?.text=unixTime(weatherList.sys.sunrise)
                binding?.tvSunsetTime?.text=unixTime(weatherList.sys.sunset)
                binding?.tvHumidity?.text="Humidity " + weatherList.main.humidity.toString() + "%"
                binding?.tvMin?.text= weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString()) + " MIN"
                binding?.tvMax?.text=weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString()) + " MAX"
                binding?.tvSpeed?.text=weatherList.wind.speed.toString()
                binding?.tvName?.text=weatherList.name
                binding?.tvCountry?.text=weatherList.sys.country
                when(weatherList.weather[i].icon){
                    "01d"->binding?.ivMain?.setImageResource(R.drawable.sunny)
                    "02d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "04n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10d"->binding?.ivMain?.setImageResource(R.drawable.rain)
                    "11d"->binding?.ivMain?.setImageResource(R.drawable.storm)
                    "13d"->binding?.ivMain?.setImageResource(R.drawable.snowflake)
                    "01n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "02n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "03n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "10n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
                    "11n"->binding?.ivMain?.setImageResource(R.drawable.rain)
                    "13n"->binding?.ivMain?.setImageResource(R.drawable.snowflake)
                }
                Toast.makeText(this,"ALL WELL",Toast.LENGTH_SHORT).show()
            }
        }
//        for (i in weatherList.weather.indices){
//            binding?.tvMain?.text=weatherList.weather[i].main
//            binding?.tvMainDesc?.text=weatherList.weather[i].description
//            binding?.tvTemp?.text= weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
//            binding?.tvSunriseTime?.text=unixTime(weatherList.sys.sunrise)
//            binding?.tvSunsetTime?.text=unixTime(weatherList.sys.sunset)
//            binding?.tvHumidity?.text="Humidity " + weatherList.main.humidity.toString() + "%"
//            binding?.tvMin?.text= weatherList.main.temp_min.toString() + " MIN"
//            binding?.tvMax?.text=weatherList.main.temp_max.toString() + " MAX"
//            binding?.tvSpeed?.text=weatherList.wind.speed.toString()
//            binding?.tvName?.text=weatherList.name
//            binding?.tvCountry?.text=weatherList.sys.country
//            when(weatherList.weather[i].icon){
//                "01d"->binding?.ivMain?.setImageResource(R.drawable.sunny)
//                "02d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "03d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "04d"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "04n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "10d"->binding?.ivMain?.setImageResource(R.drawable.rain)
//                "11d"->binding?.ivMain?.setImageResource(R.drawable.storm)
//                "13d"->binding?.ivMain?.setImageResource(R.drawable.snowflake)
//                "01n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "02n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "03n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "10n"->binding?.ivMain?.setImageResource(R.drawable.cloud)
//                "11n"->binding?.ivMain?.setImageResource(R.drawable.rain)
//                "13n"->binding?.ivMain?.setImageResource(R.drawable.snowflake)
//            }
//            Toast.makeText(this,"ALL WELL",Toast.LENGTH_SHORT).show()
//        }
    }

    private fun getUnit(value:String):String?{
        var value= "°C"
        if ("US" == value || "LR"== value || "MM"== value){
            value="°F"
        }
        return value
    }

    private fun unixTime(timex:Long):String{
        val date=Date(timex *1000L)
        val sdf=SimpleDateFormat("HH:mm")
        //val sdf=SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        sdf.timeZone= TimeZone.getDefault()
        return sdf.format(date)
    }

}