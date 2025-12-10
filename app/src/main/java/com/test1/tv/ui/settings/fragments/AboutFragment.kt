package com.test1.tv.ui.settings.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.test1.tv.BuildConfig
import com.test1.tv.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set version info from BuildConfig
        view.findViewById<TextView>(R.id.version_name).text = "Version ${BuildConfig.VERSION_NAME}"
        view.findViewById<TextView>(R.id.version_code).text = "Build ${BuildConfig.VERSION_CODE}"
    }
}
