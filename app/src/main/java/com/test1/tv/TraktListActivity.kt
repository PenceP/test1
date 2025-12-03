package com.test1.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import com.test1.tv.ui.traktlist.TraktListFragment

@AndroidEntryPoint
class TraktListActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_USERNAME = "extra_trakt_username"
        private const val EXTRA_LIST_ID = "extra_trakt_list_id"
        private const val EXTRA_TITLE = "extra_trakt_title"
        private const val EXTRA_TRAKT_URL = "extra_trakt_url"

        fun newIntent(
            context: Context,
            username: String,
            listId: String,
            title: String,
            traktUrl: String
        ): Intent {
            return Intent(context, TraktListActivity::class.java).apply {
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TRAKT_URL, traktUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_list)

        if (savedInstanceState == null) {
            val username = intent.getStringExtra(EXTRA_USERNAME) ?: return finish()
            val listId = intent.getStringExtra(EXTRA_LIST_ID) ?: return finish()
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Trakt List"
            val traktUrl = intent.getStringExtra(EXTRA_TRAKT_URL) ?: ""

            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.trakt_list_container,
                    TraktListFragment.newInstance(username, listId, title, traktUrl),
                    "trakt_list"
                )
                .commitNow()
        }
    }
}
