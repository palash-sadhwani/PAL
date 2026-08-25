package com.palash.pal.ui.navigation

sealed class Destination(val route: String) {
    object Chat : Destination("chat")
    object Settings : Destination("settings")
}
