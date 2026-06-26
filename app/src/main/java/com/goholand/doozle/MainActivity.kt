package com.goholand.doozle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goholand.doozle.ui.screens.picker.ProjectPickerScreen

class MainActivity : ComponentActivity() {
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
}

@Composable
private fun DoozleNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "picker") {
        composable("picker") {
            ProjectPickerScreen(
                onProjectSelected = { project ->
                    navController.navigate("compare/${project.id}")
                }
            )
        }
        composable("compare/{projectId}") { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            // Stub — will be replaced with ComparisonScreen in Phase 5
            androidx.compose.material3.Text("Project: $projectId")
        }
    }
}
