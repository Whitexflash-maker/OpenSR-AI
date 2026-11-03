package com.featherx.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.featherx.app.feature.gamespace.GameSpaceViewModel
import com.featherx.app.feature.upscaler.UpscalerViewModel
import com.featherx.app.ui.FeatherXApp
import com.featherx.app.ui.theme.FeatherXTheme

class MainActivity : ComponentActivity() {

    // Owned by the Activity's ViewModelStore: survives rotation and tab switches.
    private val upscalerViewModel: UpscalerViewModel by viewModels()
    private val gameSpaceViewModel: GameSpaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            FeatherXTheme {
                FeatherXApp(upscalerViewModel, gameSpaceViewModel)
            }
        }
    }
}
