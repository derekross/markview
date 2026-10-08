package com.derekross.markview

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.derekross.markview.core.data.DocumentSource
import com.derekross.markview.feature.home.FolderScreen
import com.derekross.markview.feature.home.HomeScreen
import com.derekross.markview.feature.reader.ReaderScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ReaderRoute(val key: String, val treeUri: String? = null) {
    val source: DocumentSource get() = DocumentSource.fromKey(key, treeUri)

    companion object {
        fun of(source: DocumentSource) = ReaderRoute(
            key = source.key,
            treeUri = (source as? DocumentSource.Local)?.treeUri?.toString(),
        )
    }
}

@Serializable
data class FolderRoute(val treeUri: String, val documentId: String? = null, val title: String)

private fun NavHostController.openDocument(source: DocumentSource) = navigate(ReaderRoute.of(source))

@Composable
fun MarkviewNavHost(pendingSource: DocumentSource?, onPendingConsumed: () -> Unit) {
    val navController = rememberNavController()

    LaunchedEffect(pendingSource) {
        pendingSource?.let {
            navController.openDocument(it)
            onPendingConsumed()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(320)) },
            exitTransition = { scaleOut(tween(320), targetScale = 0.96f) + fadeOut(tween(220)) },
            popEnterTransition = { scaleIn(tween(320), initialScale = 0.96f) + fadeIn(tween(320)) },
            popExitTransition = { slideOutHorizontally(tween(320)) { it / 5 } + fadeOut(tween(220)) },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenDocument = navController::openDocument,
                    onOpenFolder = { folder -> navController.navigate(FolderRoute(folder.treeUri, null, folder.name)) },
                )
            }
            composable<FolderRoute> { entry ->
                val route = entry.toRoute<FolderRoute>()
                FolderScreen(
                    treeUri = route.treeUri,
                    documentId = route.documentId,
                    title = route.title,
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { tree, docId, title -> navController.navigate(FolderRoute(tree, docId, title)) },
                    onOpenDocument = navController::openDocument,
                )
            }
            composable<ReaderRoute> { entry ->
                val route = entry.toRoute<ReaderRoute>()
                ReaderScreen(
                    source = route.source,
                    onBack = {
                        if (!navController.popBackStack()) navController.navigate(HomeRoute)
                    },
                    onOpenDocument = navController::openDocument,
                )
            }
        }
    }
}
