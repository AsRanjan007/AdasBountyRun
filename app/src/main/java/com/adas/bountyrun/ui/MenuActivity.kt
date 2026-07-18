package com.adas.bountyrun.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.adas.bountyrun.R
import com.adas.bountyrun.config.AdasConfig
import com.adas.bountyrun.config.AdasFeature
import com.adas.bountyrun.config.CountryProfile
import com.adas.bountyrun.config.DrivingSide
import com.adas.bountyrun.config.EnvironmentType
import com.adas.bountyrun.config.GameMode
import com.adas.bountyrun.config.GameSetup
import com.adas.bountyrun.config.TimeOfDay
import com.adas.bountyrun.config.VehicleSpec
import com.adas.bountyrun.config.Weather

/**
 * The setup menu (spec §2 steps 1-6, §16 menus). Every selection writes into the
 * shared [GameSetup]; driving side is derived automatically from the country
 * (spec §4). ADAS features are toggled per-run.
 */
class MenuActivity : AppCompatActivity() {

    private val setup get() = GameSetup.current
    private val environments = EnvironmentType.values().filter { it.mvp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        setupCountry()
        setupSpinner(R.id.spEnvironment, environments.map { it.displayName }) { setup.environment = environments[it] }
        setupSpinner(R.id.spVehicle, VehicleSpec.ALL.map { it.displayName }) { setup.vehicle = VehicleSpec.ALL[it] }
        setupSpinner(R.id.spWeather, Weather.values().map { it.displayName }) { setup.weather = Weather.values()[it] }
        setupSpinner(R.id.spTime, TimeOfDay.values().map { it.displayName }) { setup.timeOfDay = TimeOfDay.values()[it] }
        setupSpinner(R.id.spMode, GameMode.values().map { it.displayName }) { setup.mode = GameMode.values()[it] }

        buildAdasToggles()

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }
    }

    private fun setupCountry() {
        val countries = CountryProfile.ALL
        val sp = findViewById<Spinner>(R.id.spCountry)
        sp.adapter = adapter(countries.map { it.displayName })
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setup.country = countries[position]
                val side = if (setup.country.drivingSide == DrivingSide.LEFT) "Left-hand traffic" else "Right-hand traffic"
                val wheel = if (setup.country.rightHandDrive) "RHD" else "LHD"
                findViewById<TextView>(R.id.tvDrivingSide).text = "$side • $wheel"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSpinner(id: Int, labels: List<String>, onSelect: (Int) -> Unit) {
        val sp = findViewById<Spinner>(id)
        sp.adapter = adapter(labels)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelect(position)
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun adapter(labels: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.WHITE); return v
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    /** One switch per ADAS feature (spec §2 step 5). Reset config, then toggle. */
    private fun buildAdasToggles() {
        val container = findViewById<LinearLayout>(R.id.adasToggles)
        setup.adas = AdasConfig()
        val cfg = setup.adas
        for (feature in AdasFeature.values()) {
            val sw = Switch(this).apply {
                text = feature.displayName
                setTextColor(Color.rgb(199, 205, 212))
                isChecked = cfg.isOn(feature)
                setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                    val on = cfg.isOn(feature)
                    if (checked != on) cfg.toggle(feature)
                }
            }
            container.addView(sw)
        }
    }
}
