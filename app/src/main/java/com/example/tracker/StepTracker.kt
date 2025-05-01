package com.example.tracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class StepTrackerFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var isStepSensorPresent = false

    private var totalSteps = 0
    private var previousTotalSteps = 0

    private lateinit var stepCountText: TextView
    private lateinit var resetButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.step_tracker_fragment, container, false)

        stepCountText = view.findViewById(R.id.stepCountText)
        resetButton = view.findViewById(R.id.resetButton)

        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            isStepSensorPresent = true
        } else {
            Toast.makeText(requireContext(), "Датчик шагов не обнаружен", Toast.LENGTH_SHORT).show()
            isStepSensorPresent = false
        }
        loadData()

        resetButton.setOnClickListener {
            resetSteps()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        if (isStepSensorPresent) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isStepSensorPresent) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            totalSteps = event.values[0].toInt()
            val currentSteps = totalSteps - previousTotalSteps
            stepCountText.text = "Шагов: $currentSteps"
            saveData()
        }
    }
    private fun resetSteps() {
        stepCountText.text = "Шагов: 0"
        previousTotalSteps = totalSteps
        saveData()
    }

    private fun saveData() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("previousSteps", previousTotalSteps)
            apply()
        }
    }

    private fun loadData() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        previousTotalSteps = sharedPref.getInt("previousSteps", 0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}