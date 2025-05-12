package com.example.tracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location


class CardAndTracker: Fragment(){

    private lateinit var mapView: MapView
    private lateinit var chronometer: Chronometer
    private lateinit var distanceText: TextView
    private lateinit var stepsText: TextView
    private lateinit var pauseButton: Button
    private lateinit var finishButton: Button
    private lateinit var permissionLauncher: ActivityResultLauncher<String>


    private var isTracking = false
    private var isPaused = false
    private var totalDistance = 0f
    private var stepsCount = 0
    private var lastLocation: Location? = null
    private val routePoints = mutableListOf<Point>()
    private var polylineAnnotation: PolylineAnnotation? = null
    private lateinit var polylineAnnotationManager: PolylineAnnotationManager
    private var pauseOffset: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startLocationTracking()
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showPermissionRationaleDialog()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Разрешение отклонено. Функциональность ограничена.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Требуется разрешение")
            .setMessage("Для отслеживания маршрута необходимо разрешение на геолокацию")
            .setPositiveButton("Разрешить") { _, _ ->
                // Запускаем запрос разрешения через лаунчер
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    requireContext(),
                    "Функции трекера будут ограничены",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setCancelable(false)
            .show()
    }
    private fun requestLocationPermissions() {
        when {
            hasLocationPermission() -> {
                startLocationTracking()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.card_of_exercise_fragment, container, false).apply {
            mapView = findViewById(R.id.mapView)
            chronometer = findViewById(R.id.chronometer)
            distanceText = findViewById(R.id.distanceText)
            stepsText = findViewById(R.id.stepCounterText)
            pauseButton = findViewById(R.id.pauseButton)
            finishButton = findViewById(R.id.finishButton)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        polylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()

        setupMap()
        setupButtons()
        restoreState(savedInstanceState)
    }

    private fun setupMap() {
        mapView.mapboxMap.loadStyle(
            Style.STANDARD
        ) { setupLocationComponent() }
    }


    @SuppressLint("MissingPermission")
    private fun setupLocationComponent() {
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
            locationPuck = LocationPuck2D(
                topImage = ImageHolder.from(R.drawable.mapbox_user_puck_icon)
            )
        }

        if (hasLocationPermission()) {
            startLocationTracking()
        } else {
            requestLocationPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        if (isTracking) return

        isTracking = true
        startTimer()

        // Слушатель изменения позиции индикатора
        val positionListener = OnIndicatorPositionChangedListener { point ->
            if (!isPaused) {
                updateCamera(point)
            }
        }

        // Регистрируем слушателя
        mapView.location.apply {
            addOnIndicatorPositionChangedListener(positionListener)
        }
    }


    private fun calculateDistanceAndSteps(newLocation: Location) {
        lastLocation?.let { prevLocation ->
            try {
                if (isValidLocation(prevLocation) && isValidLocation(newLocation)) {
                    val distance = prevLocation.distanceTo(newLocation)
                    if (distance > 5.0f) {  // Игнорируем перемещения меньше 5 метров
                        totalDistance += distance
                        stepsCount += (distance / 0.75).toInt() // Примерные шаги
                    }else
                        totalDistance.toInt() == 0
                }else
                        totalDistance.toInt() == 0
            } catch (e: Exception) {
                Log.e("Локация", "Ошибка расчёта дистанции: ${e.message}")
            }
        }
        lastLocation = newLocation
    }

    private fun isValidLocation(location: Location): Boolean {
        return location.latitude != 0.0 || location.longitude != 0.0
    }

    private fun updatePolyline() {
        polylineAnnotation?.let { polylineAnnotationManager.delete(it) }

        polylineAnnotation = polylineAnnotationManager.create(
            PolylineAnnotationOptions()
                .withPoints(routePoints)
                .withLineColor("#3F51B5")
                .withLineWidth(5.0)
        )
    }

    private fun updateCamera(point: Point, bearing: Double = 0.0) {
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(point)
                .zoom(15.0)
                .bearing(bearing)
                .build()
        )
    }

    private fun updateUI() {
        distanceText.text = "Дистанция: ${"%.2f".format(totalDistance / 1000)} км"
        stepsText.text = "Шаги: $stepsCount"
    }

    private fun startTimer() {
        chronometer.base = SystemClock.elapsedRealtime() - pauseOffset
        chronometer.start()
    }

    private fun pauseTimer() {
        chronometer.stop()
        pauseOffset = SystemClock.elapsedRealtime() - chronometer.base
    }

    private fun setupButtons() {
        pauseButton.setOnClickListener {
            if (isPaused) {
                isPaused = false
                pauseButton.text = "Пауза"
                startTimer()
            } else {
                isPaused = true
                pauseButton.text = "Продолжить"
                pauseTimer()
            }
        }

        finishButton.setOnClickListener {
            stopWorkout()
//            findNavController().navigateUp()
        }
    }

    private fun stopWorkout() {
        chronometer.stop()
        saveWorkoutData()
        mapView.location.apply {
            removeOnIndicatorPositionChangedListener { }
            removeOnIndicatorBearingChangedListener { }
        }
    }

    private fun saveWorkoutData() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putFloat("последняя дистанция", totalDistance)
            putInt("последние шаги", stepsCount)
            putLong("последнее_время", SystemClock.elapsedRealtime() - chronometer.base)
            apply()
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            totalDistance = it.getFloat("distance", 0f)
            stepsCount = it.getInt("шаги", 0)
            pauseOffset = it.getLong("Время", 0)
            updateUI()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat("дистанция", totalDistance)
        outState.putInt("шаги", stepsCount)
        outState.putLong("Время",
            if (isPaused) pauseOffset else SystemClock.elapsedRealtime() - chronometer.base)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        stopWorkout()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED


//    companion object {
//        private fun requestLocationPermissions(cardAndTracker: CardAndTracker) {
//            cardAndTracker.permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//    }
}