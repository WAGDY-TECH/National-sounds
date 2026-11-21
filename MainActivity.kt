package org.nationalsounds.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import org.nationalsounds.app.ui.MainScreen
import org.nationalsounds.app.ui.MainViewModel
import org.nationalsounds.app.ui.NationalSoundsTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
            if (uri != null) vm.saveTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val dark = vm.darkOverride ?: isSystemInDarkTheme()
            NationalSoundsTheme(darkTheme = dark) {
                MainScreen(vm = vm, onSave = { saveLauncher.launch(vm.suggestedFileName()) })
            }
        }
    }
}
