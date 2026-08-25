package com.palash.pal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.palash.pal.ui.navigation.AppNavGraph
import com.palash.pal.ui.theme.PALTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PALTheme {
                val appContainer = remember {
                    (application as PALApplication).appContainer
                }
                AppNavGraph(appContainer = appContainer)
            }
        }
    }
}
