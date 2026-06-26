package com.goholand.doozle

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goholand.doozle.data.ProjectRepository
import com.goholand.doozle.engine.*
import com.goholand.doozle.ui.screens.compare.ComparisonScreen
import com.goholand.doozle.ui.screens.compare.ComparisonViewModel
import com.goholand.doozle.ui.screens.picker.ProjectPickerScreen
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val projectRepository: ProjectRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    DoozleNavigation()
                }
            }
        }
    }

    @Composable
    private fun DoozleNavigation() {
        val navController = rememberNavController()
        val context = LocalContext.current

        NavHost(navController = navController, startDestination = "picker") {
            composable("picker") {
                ProjectPickerScreen(
                    onProjectSelected = { project ->
                        navController.navigate("compare/${Uri.encode(project.folderUri)}")
                    }
                )
            }
            composable("compare/{folderUri}") { backStackEntry ->
                val folderUri = backStackEntry.arguments?.getString("folderUri")?.let {
                    Uri.decode(it)
                } ?: ""

                val config = EngineConfig()
                val fs = remember(folderUri) {
                    SafFileSystem(context, Uri.parse(folderUri))
                }
                val tree = remember(folderUri) {
                    BStarTree(fs, "_ranked", config)
                }
                val unseenManager = remember(folderUri) {
                    UnseenManager(fs, "")
                }
                val viewModel = remember(folderUri) {
                    ComparisonViewModel(tree, unseenManager, config)
                }

                LaunchedEffect(folderUri) {
                    viewModel.initialize(fs, "_unseen")
                }

                val state by viewModel.state.collectAsState()

                ComparisonScreen(
                    state = state,
                    onWinnerSelected = { winner, loser ->
                        viewModel.onWinnerSelected(winner, loser)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
