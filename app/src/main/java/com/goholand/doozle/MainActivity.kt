package com.goholand.doozle

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
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
            MaterialTheme(colorScheme = darkColorScheme()) {
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
                val folderUriStr = backStackEntry.arguments?.getString("folderUri") ?: ""
                val parsedUri = Uri.parse(folderUriStr)

                // Check we still have permission
                val hasPermission = remember(folderUriStr) {
                    val persisted = context.contentResolver.persistedUriPermissions
                    Log.d("Doozle", "Checking permission for: $folderUriStr")
                    Log.d("Doozle", "Persisted: ${persisted.map { it.uri.toString() }}")
                    persisted.any { perm ->
                        perm.isReadPermission &&
                            perm.uri.toString() == folderUriStr
                    }
                }

                if (!hasPermission) {
                    androidx.compose.material3.Text("Permission lost. Please re-select the folder.")
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        navController.popBackStack()
                    }
                    return@composable
                }

                val config = EngineConfig()
                val fs = remember(folderUriStr) {
                    SafFileSystem(context, parsedUri)
                }
                val tree = remember(folderUriStr) {
                    BStarTree(fs, "_ranked", config)
                }
                val unseenManager = remember(folderUriStr) {
                    UnseenManager(fs, "")
                }
                val viewModel = remember(folderUriStr) {
                    ComparisonViewModel(tree, unseenManager, config)
                }

                LaunchedEffect(folderUriStr) {
                    viewModel.initialize(fs, "")
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
