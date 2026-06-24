package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Shared ViewModel scoped to the navigation graph / main activity
    val viewModel: MainViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToCapture = { navController.navigate("capture") },
                onNavigateToCrop = { navController.navigate("crop") },
                onNavigateToDetail = { doc ->
                    viewModel.setActiveDocument(doc)
                    navController.navigate("detail")
                }
            )
        }

        composable("capture") {
            CameraCaptureScreen(
                viewModel = viewModel,
                onNavigateToCrop = { navController.navigate("crop") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("crop") {
            CropScreen(
                viewModel = viewModel,
                onNavigateToFilter = { navController.navigate("filter") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("filter") {
            FilterScreen(
                viewModel = viewModel,
                onSaveSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("detail") {
            DetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
