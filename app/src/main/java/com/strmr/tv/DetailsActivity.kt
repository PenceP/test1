package com.strmr.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.strmr.tv.ui.details.DetailsFragment
import com.strmr.tv.data.model.ContentItem
import dagger.hilt.android.AndroidEntryPoint

/**
 * Details activity class that loads [DetailsFragment].
 */
@AndroidEntryPoint
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

        fun start(context: Context, item: ContentItem) {
            val intent = Intent(context, DetailsActivity::class.java).apply {
                putExtra(CONTENT_ITEM, item)
            }
            context.startActivity(intent)
        }
    }
}
