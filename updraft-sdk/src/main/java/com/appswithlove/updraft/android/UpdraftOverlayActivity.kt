package com.appswithlove.updraft.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appswithlove.updraft.UpdraftEvent
import com.appswithlove.updraft.ui.dialogs.FeedbackDisabledDialog
import com.appswithlove.updraft.ui.dialogs.FeedbackHintDialog
import com.appswithlove.updraft.ui.dialogs.UpdateAvailableDialog
import com.appswithlove.updraft.Updraft

class UpdraftOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated instance (e.g. rotation) must not consume an event queued for a new overlay.
        val event = if (savedInstanceState == null) pendingEvents.removeFirstOrNull() else null
        if (event == null) {
            finish()
            return
        }
        setContent {
            when (event) {
                is UpdraftEvent.UpdateAvailable -> UpdateAvailableDialog(
                    event = event,
                    onOpen = { url -> Updraft.openUpdateUrl(url); finish() },
                    onLater = { finish() },
                )
                UpdraftEvent.ShowFeedbackHint -> FeedbackHintDialog(onDismiss = { finish() })
                UpdraftEvent.FeedbackDisabled -> FeedbackDisabledDialog(onDismiss = { finish() })
                else -> finish()
            }
        }
    }

    companion object {
        // Main-thread only. A queue, not a single slot: several events can arrive within one
        // activity-start window (hint, update, feedback state), each getting its own overlay.
        private val pendingEvents = ArrayDeque<UpdraftEvent>()

        fun launch(context: Context, event: UpdraftEvent) {
            pendingEvents.addLast(event)
            context.startActivity(
                Intent(context, UpdraftOverlayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
