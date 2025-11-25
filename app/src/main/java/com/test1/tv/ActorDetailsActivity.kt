package com.test1.tv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.test1.tv.data.model.ContentItem
import com.test1.tv.ui.actor.ActorDetailsFragment
import android.content.Intent
import android.content.Context

/**
 * Actor details activity class that loads [ActorDetailsFragment].
 */
class ActorDetailsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_actor_details)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.actor_details_fragment, ActorDetailsFragment())
                .commitNow()
        }
    }

    companion object {
        const val PERSON_ID = "person_id"
        const val PERSON_NAME = "person_name"
        const val ORIGIN_CONTENT = "origin_content"

        fun start(
            context: android.content.Context,
            personId: Int,
            personName: String?,
            originContent: ContentItem? = null
        ) {
            val intent = Intent(context, ActorDetailsActivity::class.java).apply {
                putExtra(PERSON_ID, personId)
                putExtra(PERSON_NAME, personName)
                originContent?.let { putExtra(ORIGIN_CONTENT, it) }
            }
            context.startActivity(intent)
        }
    }

    override fun onBackPressed() {
        val origin = intent.getParcelableExtra<ContentItem>(ORIGIN_CONTENT)
        if (isTaskRoot && origin != null) {
            val detailsIntent = Intent(this, DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.CONTENT_ITEM, origin)
            }
            startActivity(detailsIntent)
            finish()
            return
        }
        super.onBackPressed()
    }
}
