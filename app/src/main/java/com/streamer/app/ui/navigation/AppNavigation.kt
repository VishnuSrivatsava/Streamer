package com.streamer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.streamer.app.data.model.MediaItem
import com.streamer.app.ui.screens.BrowseScreen
import com.streamer.app.ui.screens.DetailScreen
import com.streamer.app.ui.screens.HomeScreen
import com.streamer.app.ui.screens.PlayerScreen

/**
 * Shared navigation state holder.
 * Avoids passing URLs/JSON through Compose Navigation route args
 * which mangles percent-encoded characters.
 */
object NavArgs {
    var browseName: String = ""
    var browsePath: String = ""
    var detailItem: MediaItem? = null
    var playerUrl: String = ""
    var playerTitle: String = ""
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onCategoryClick = { name, path ->
                    NavArgs.browseName = name
                    NavArgs.browsePath = path
                    navController.navigate("browse")
                }
            )
        }

        composable("browse") {
            BrowseScreen(
                categoryName = NavArgs.browseName,
                categoryPath = NavArgs.browsePath,
                onFileClick = { item ->
                    NavArgs.detailItem = item
                    navController.navigate("detail")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("detail") {
            val item = NavArgs.detailItem
            if (item != null) {
                DetailScreen(
                    mediaItem = item,
                    onPlayClick = { url, title ->
                        NavArgs.playerUrl = url
                        NavArgs.playerTitle = title
                        navController.navigate("player")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("player") {
            PlayerScreen(
                streamUrl = NavArgs.playerUrl,
                title = NavArgs.playerTitle,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
