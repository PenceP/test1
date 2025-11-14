package com.test1.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.test1.tv.ui.details.DetailsFragment

/**
 * Details activity class that loads [DetailsFragment].
 */
class DetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_fragment, DetailsFragment())
                .commitNow()
        }
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
        const val CONTENT_ITEM = "content_item"
    }
}
