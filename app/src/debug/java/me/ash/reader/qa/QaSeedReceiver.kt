package me.ash.reader.qa

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only: `am broadcast -a me.ash.reader.QA_SEED -p me.ash.reader --es scenario <name>`.
 * Replies `seeded <name>` (RESULT_OK) once the data is committed, or the error (RESULT_CANCELED).
 */
class QaSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val result = QaSeeder.seed(context.applicationContext, scenario)
            pending.resultCode = if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED
            pending.resultData = result.getOrElse { "seed failed: ${it.message}" }.take(200)
            pending.finish()
        }
    }
}
