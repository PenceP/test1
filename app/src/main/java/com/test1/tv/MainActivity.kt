package com.test1.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.test1.tv.ui.home.HomeFragment

/**
 * Loads [HomeFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, HomeFragment())
                .commitNow()
        }
    }
}