package com.palash.pal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.palash.pal.di.AppContainer
import com.palash.pal.ui.chat.ChatRoute
import com.palash.pal.ui.settings.SettingsRoute
import com.palash.pal.ui.settings.SettingsViewModel

@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Chat.route,
        modifier = modifier
    ) {
        composable(Destination.Chat.route) {
            ChatRoute(
                appContainer = appContainer,
                onOpenSettings = { navController.navigate(Destination.Settings.route) }
            )
        }

        composable(Destination.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsRoute(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
