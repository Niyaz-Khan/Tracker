package com.example.tracker

import android.annotation.SuppressLint
import android.graphics.Point
import android.health.connect.datatypes.ExerciseRoute
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit

class CardAndTracker: Fragment(), PermissionListener {

    private lateinit var mapView: MapView
    private lateinit var stepCounterText: TextView
    private lateinit var distanceText: TextView
    private lateinit var timerText: TextView
    private lateinit var chronometer: Chronometer

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var locationEngine: LocationEngine
    private var lastLocation: ExerciseRoute.Location? = null
    private var totalDistance: Float = 0f
    private var startTime: Long = 0L
    private var elapsedTime: Long = 0L

    private val decimalFormat = DecimalFormat("#.##")
    private var timer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.card_of_exercise_fragment, container, false)

        mapView = view.findViewById(R.id.mapView)
        stepCounterText = view.findViewById(R.id.stepCounterText)
        distanceText = view.findViewById(R.id.distanceText)
        timerText = view.findViewById(R.id.timerText)
        chronometer = view.findViewById(R.id.chronometer)

        permissionsManager = PermissionsManager(this)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Восстановление состояния
        savedInstanceState?.let {
            totalDistance = it.getFloat("totalDistance", 0f)
            elapsedTime = it.getLong("elapsedTime", 0L)
            updateDistanceUI()
        }

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)

        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
            initLocationEngine()
            startTimer()
        } else {
            permissionsManager.requestLocationPermissions(this)
        }
    }

    private fun startTimer() {
        //Использование Chronometer
        chronometer.base = SystemClock.elapsedRealtime() - elapsedTime
        chronometer.start()

    startTime = SystemClock.elapsedRealtime() - elapsedTime
    }

    private fun updateTimerUI() {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60

        timerText.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    @SuppressLint("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(requireContext())

        val request = LocationEngineRequest.Builder(1000)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(1500)
            .build()

        locationEngine.requestLocationUpdates(request, locationCallback, mainLooper)
        locationEngine.getLastLocation(locationCallback)
    }

    private val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location = result.lastLocation ?: return

            // Обновляем местоположение на карте
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(location.longitude, location.latitude))
                    .zoom(15.0)
                    .build()
            )

            // Рассчитываем пройденное расстояние
            lastLocation?.let { last ->
                val distance = last.distanceTo(location)
                totalDistance += distance

                activity?.runOnUiThread {
                    updateDistanceUI()
                }
            }

            lastLocation = location
        }

        override fun onFailure(exception: Exception) {
            // Обработка ошибок
        }
    }

    private fun updateDistanceUI() {
        val distanceInKm = totalDistance / 1000
        distanceText.text = "Дистанция: ${decimalFormat.format(distanceInKm)} км"

        val steps = (totalDistance / 0.75).toInt()
        stepCounterText.text = "Шаги: $steps"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        // Объяснение необходимости разрешений
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            initLocationEngine()
            startTimer()
        } else {
            // Обработка отказа в разрешениях
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("totalDistance", totalDistance)
        outState.putLong("elapsedTime",
            if (::chronometer.isInitialized) SystemClock.elapsedRealtime() - chronometer.base
            else elapsedTime)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        if (::locationEngine.isInitialized) {
            locationEngine.removeLocationUpdates(locationCallback)
        }
        chronometer.stop()
        timer?.cancel()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
            chronometer.start()
            startTimer()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        timer?.cancel()
    }
}