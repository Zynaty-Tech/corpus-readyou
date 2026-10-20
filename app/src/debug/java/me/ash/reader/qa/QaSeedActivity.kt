package me.ash.reader.qa

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.android.MainActivity

/**
 * Debug-only: `am start -W -n me.ash.reader/me.ash.reader.qa.QaSeedActivity [--es scenario <name>]`.
 * Loads the scenario (default `feeds-mixed`), then opens MainActivity and finishes.
 */
class QaSeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: QaSeeder.DEFAULT
        lifecycleScope.launch {
            val result = QaSeeder.seed(applicationContext, scenario)
            Log.i("QaSeed", result.getOrElse { "seed failed: ${it.message}" })
            setResult(if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            startActivity(Intent(this@QaSeedActivity, MainActivity::class.java))
            finish()
        }
    }
}
