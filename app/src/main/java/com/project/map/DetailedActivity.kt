package com.project.map

import android.location.Address
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textview.MaterialTextView
import com.project.map.databinding.ActivityDetailedBinding

class DetailedActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailedBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val address = intent.extras?.getParcelable<Address>("address")
        bindDataWithViews(address)
    }

    private fun bindDataWithViews(address: Address?) {
        binding.apply {
            setTextOnView(tvLocaleValue, address?.locale.toString())

            setTextOnView(tvFeatureValue, address?.featureName)
            setTextOnView(tvAdminAreaValue, address?.adminArea)
            setTextOnView(tvSubAdminAreaValue, address?.subAdminArea)
            setTextOnView(tvLocalityValue, address?.locality)
            setTextOnView(tvSubLocalityValue, address?.subLocality)
            setTextOnView(tvPostalCodeValue, address?.postalCode)
            setTextOnView(tvCountryCodeValue, address?.countryCode)
            setTextOnView(tvCountryNameValue, address?.countryName)
            setTextOnView(tvLatitudeValue, address?.latitude.toString())
            setTextOnView(tvLongitudeValue, address?.longitude.toString())

        }
    }

    private fun setTextOnView(view: MaterialTextView, value: String?) {
        if (value != null && value.isNotEmpty() && !value.contains("null", true)) {
            view.text = value
        } else {
            view.text = getString(R.string.unknown)
        }
    }
}