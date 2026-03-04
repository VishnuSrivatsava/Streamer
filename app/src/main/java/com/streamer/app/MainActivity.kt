package com.streamer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.streamer.app.ui.navigation.AppNavigation
import com.streamer.app.ui.theme.StreamerBlack
import com.streamer.app.ui.theme.StreamerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamerTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(StreamerBlack),
                    shape = RectangleShape
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
