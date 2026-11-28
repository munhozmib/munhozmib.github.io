package io.github.munhozmib.cifrario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.munhozmib.cifrario.data.AppSettings
import io.github.munhozmib.cifrario.ui.CifrarioNavHost
import io.github.munhozmib.cifrario.ui.theme.CifrarioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = intent?.takeIf { it.action == android.content.Intent.ACTION_SEND }
            ?.getStringExtra(android.content.Intent.EXTRA_TEXT)
            ?.let { Regex("https?://\\S+").find(it)?.value }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            CifrarioTheme(settings.theme) {
                CifrarioNavHost(sharedUrl = sharedUrl)
            }
        }
    }
}
