package com.strmr.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import com.strmr.tv.data.model.trakt.TraktMediaList
import com.strmr.tv.ui.traktmedia.TraktMediaFragment

@AndroidEntryPoint
class TraktMediaActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_CATEGORY = "extra_trakt_media_category"

        fun newIntent(context: Context, category: TraktMediaList): Intent {
            return Intent(context, TraktMediaActivity::class.java).apply {
                putExtra(EXTRA_CATEGORY, category.name)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_media)

        if (savedInstanceState == null) {
            val category = intent.getStringExtra(EXTRA_CATEGORY) ?: TraktMediaList.MOVIE_COLLECTION.name
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.trakt_media_container,
                    TraktMediaFragment.newInstance(category),
                    "trakt_media"
                )
                .commitNow()
        }
    }
}
