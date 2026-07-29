package com.aiimagestudio.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiimagestudio.presentation.gallery.GalleryScreen
import com.aiimagestudio.presentation.home.HomeScreen
import com.aiimagestudio.presentation.modelmanager.ModelManagerScreen
import com.aiimagestudio.presentation.settings.SettingsScreen

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Gallery : Destination("gallery")
    data object ModelManager : Destination("model_manager")
    data object Settings : Destination("settings")
}

@Composable
fun AppNavigation() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(
                onOpenGallery = { navController.navigate(Destination.Gallery.route) },
                onOpenModelManager = { navController.navigate(Destination.ModelManager.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) }
            )
        }
        composable(Destination.Gallery.route) {
            GalleryScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.ModelManager.route) {
            ModelManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
