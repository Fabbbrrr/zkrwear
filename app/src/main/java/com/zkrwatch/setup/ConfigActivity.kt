package com.zkrwatch.setup

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zkrwatch.data.store.ConfigStore
import com.zkrwatch.presentation.MainActivity
import com.zkrwatch.presentation.ZkrTheme
import java.io.ByteArrayInputStream
import java.util.Properties

/**
 * Headless setup entry point. The `setup.sh` / `setup.ps1` script installs the
 * keyless APK and then launches this activity with the user's keys as a base64
 * intent extra:
 *
 *   adb shell am start -n com.zkrwatch/com.zkrwatch.setup.ConfigActivity \
 *       --es cfg <base64 of keys.txt>
 *
 * The blob is a base64-encoded properties file (same field names as keys.txt);
 * it is decrypted into the Keystore-backed [ConfigStore] and never persisted in
 * plaintext. No keys are ever compiled into the APK.
 */
class ConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ok = runCatching { importConfig(intent.getStringExtra(EXTRA_CFG)) }.getOrDefault(false)
        setContent {
            ZkrTheme { ConfigResult(ok = ok, onOpen = { openApp() }) }
        }
    }

    private fun importConfig(blob: String?): Boolean {
        if (blob.isNullOrBlank()) return false
        val bytes = Base64.decode(blob, Base64.DEFAULT)
        val props = Properties().apply { load(ByteArrayInputStream(bytes)) }
        val values = props.stringPropertyNames().associateWith { props.getProperty(it) }
        val store = ConfigStore(this)
        store.save(values)
        return store.isConfigured()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_CFG = "cfg"
    }
}

@Composable
private fun ConfigResult(ok: Boolean, onOpen: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (ok) "Configured ✓" else "Config failed",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (ok) "Keys imported securely." else "Check keys.txt has all fields.",
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
            if (ok) {
                Chip(
                    label = { Text("Open") },
                    onClick = onOpen,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
